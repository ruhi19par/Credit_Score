package com.credbridge.backend.document;

import com.credbridge.backend.scoring.VerifiedScoringService;
import com.credbridge.backend.privacy.PrivacyService;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DocumentProcessingService {

    private static final Pattern INCOME_PATTERN = Pattern.compile("(?i)(?:salary|net\\s*pay|gross\\s*pay|monthly\\s*income|income)\\D+(\\d[\\d,]*(?:\\.\\d+)?)");
    private static final Pattern EXPENSE_PATTERN = Pattern.compile("(?i)(?:monthly\\s*)?expenses?\\D+(\\d[\\d,]*(?:\\.\\d+)?)");
    private static final Pattern DEBT_PATTERN = Pattern.compile("(?i)(?:emi|loan\\s*deduction|loan\\s*payment|debt)\\D+(\\d[\\d,]*(?:\\.\\d+)?)");
    private static final Pattern DEPOSIT_PATTERN = Pattern.compile("(?i)(?:bank\\s*)?(?:deposit|deposits|credits|credit\\s*amount)\\D+(\\d[\\d,]*(?:\\.\\d+)?)");
    private static final Pattern WITHDRAWAL_PATTERN = Pattern.compile("(?i)(?:withdrawal|withdrawals|debits|outflow)\\D+(\\d[\\d,]*(?:\\.\\d+)?)");
    private static final Pattern REVENUE_PATTERN = Pattern.compile("(?i)(?:business\\s*)?revenue\\D+(\\d[\\d,]*(?:\\.\\d+)?)");
    private static final Pattern TAX_PATTERN = Pattern.compile("(?i)(?:gst|tax|cgst|sgst|igst)\\D+(\\d[\\d,]*(?:\\.\\d+)?)");
    private static final Pattern INVOICE_TOTAL_PATTERN = Pattern.compile("(?i)(?:invoice\\s*total|grand\\s*total|total\\s*amount)\\D+(\\d[\\d,]*(?:\\.\\d+)?)");
    private static final Pattern REVENUE_CONSISTENCY_PATTERN = Pattern.compile("(?i)(?:revenue\\s*consistency|consistency)\\D+(\\d[\\d,]*(?:\\.\\d+)?)");
    private static final Pattern AMOUNT_PATTERN = Pattern.compile("(\\d[\\d,]*(?:\\.\\d+)?)");

    private final DocumentRepository documentRepository;
    private final ExtractedFinancialFieldsRepository extractedFinancialFieldsRepository;
    private final OcrService ocrService;
    private final DocumentStorageService documentStorageService;
    private final VerifiedScoringService verifiedScoringService;
    private final PrivacyService privacyService;

    public DocumentProcessingService(
            DocumentRepository documentRepository,
            ExtractedFinancialFieldsRepository extractedFinancialFieldsRepository,
            OcrService ocrService,
            DocumentStorageService documentStorageService,
            VerifiedScoringService verifiedScoringService,
            PrivacyService privacyService
    ) {
        this.documentRepository = documentRepository;
        this.extractedFinancialFieldsRepository = extractedFinancialFieldsRepository;
        this.ocrService = ocrService;
        this.documentStorageService = documentStorageService;
        this.verifiedScoringService = verifiedScoringService;
        this.privacyService = privacyService;
    }

    @Transactional
    public void process(Long documentId) {
        Document document = documentRepository.findById(documentId)
                .orElseThrow(() -> new IllegalArgumentException("Document not found: " + documentId));
        privacyService.requireActiveConsent(document.getApplication());
        document.setStatus(DocumentStatus.PROCESSING);
        documentRepository.save(document);

        try {
            Path workingCopy = documentStorageService.retrieveToTemp(document);
            String extractedText;
            try {
                extractedText = ocrService.extractText(document, workingCopy);
            } finally {
                deleteWorkingCopy(workingCopy);
            }
            ExtractedFinancialFields fields = new ExtractedFinancialFields();
            fields.setDocument(document);
            fields.setExtractedText(extractedText);
            fields.setMonthlyIncome(extractStructuredValue(
                    extractedText,
                    INCOME_PATTERN,
                    BigDecimal.ZERO,
                    "net pay",
                    "gross pay",
                    "monthly income",
                    "salary",
                    "income"
            ));
            fields.setMonthlyExpenses(extractStructuredValue(
                    extractedText,
                    EXPENSE_PATTERN,
                    BigDecimal.ZERO,
                    "monthly expenses",
                    "expenses",
                    "total debit",
                    "debits"
            ));
            fields.setExistingDebtPayment(extractStructuredValue(
                    extractedText,
                    DEBT_PATTERN,
                    BigDecimal.ZERO,
                    "emi",
                    "loan deduction",
                    "loan payment",
                    "debt payment",
                    "existing debt"
            ));
            fields.setAverageMonthlyDeposits(sumStructuredValues(
                    extractedText,
                    DEPOSIT_PATTERN,
                    fields.getMonthlyIncome(),
                    "deposit",
                    "deposits",
                    "credits",
                    "credit amount",
                    "total credit"
            ));
            fields.setAverageMonthlyWithdrawals(sumStructuredValues(
                    extractedText,
                    WITHDRAWAL_PATTERN,
                    fields.getMonthlyExpenses(),
                    "withdrawal",
                    "withdrawals",
                    "debits",
                    "debit amount",
                    "outflow",
                    "total debit"
            ));
            fields.setBusinessRevenue(extractStructuredValue(
                    extractedText,
                    REVENUE_PATTERN,
                    BigDecimal.ZERO,
                    "business revenue",
                    "revenue",
                    "sales"
            ));
            fields.setTaxValue(sumStructuredValues(extractedText, TAX_PATTERN, BigDecimal.ZERO, "gst", "tax", "cgst", "sgst", "igst"));
            fields.setInvoiceTotal(extractStructuredValue(
                    extractedText,
                    INVOICE_TOTAL_PATTERN,
                    BigDecimal.ZERO,
                    "invoice total",
                    "grand total",
                    "total amount"
            ));
            fields.setRevenueConsistencyScore(extractStructuredValue(
                    extractedText,
                    REVENUE_CONSISTENCY_PATTERN,
                    BigDecimal.valueOf(60),
                    "revenue consistency",
                    "consistency"
            ));
            fields.setConfidenceScore(confidenceScore(fields, extractedText));
            fields.setCreatedAt(LocalDateTime.now());
            extractedFinancialFieldsRepository.save(fields);
            verifiedScoringService.calculateAndSave(document.getApplication(), fields);
            document.setStatus(DocumentStatus.PROCESSED);
        } catch (RuntimeException exception) {
            document.setStatus(DocumentStatus.FAILED);
            throw exception;
        } finally {
            documentRepository.save(document);
        }
    }

    @Async
    public void processAsync(Long documentId) {
        process(documentId);
    }

    private BigDecimal extractValue(String text, Pattern pattern, BigDecimal fallback) {
        Matcher matcher = pattern.matcher(text);
        if (!matcher.find()) {
            return fallback;
        }
        return parseAmount(matcher.group(1));
    }

    private BigDecimal extractStructuredValue(
            String text,
            Pattern fallbackPattern,
            BigDecimal fallback,
            String... labels
    ) {
        BigDecimal patternValue = extractValue(text, fallbackPattern, fallback);
        if (patternValue.compareTo(BigDecimal.ZERO) > 0) {
            return patternValue;
        }
        BigDecimal structuredValue = firstStructuredValue(text, labels);
        if (structuredValue.compareTo(BigDecimal.ZERO) > 0) {
            return structuredValue;
        }
        return fallback;
    }

    private BigDecimal sumStructuredValues(
            String text,
            Pattern fallbackPattern,
            BigDecimal fallback,
            String... labels
    ) {
        BigDecimal structuredTotal = sumStructuredLineValues(text, labels);
        if (structuredTotal.compareTo(BigDecimal.ZERO) > 0) {
            return structuredTotal;
        }
        return sumValues(text, fallbackPattern, fallback);
    }

    private BigDecimal firstStructuredValue(String text, String... labels) {
        for (String line : text.split("\\R")) {
            String normalizedLine = normalize(line);
            if (normalizedLine.isBlank() || !matchesAnyLabel(normalizedLine, labels)) {
                continue;
            }

            BigDecimal amount = lastAmount(line);
            if (amount.compareTo(BigDecimal.ZERO) > 0) {
                return amount;
            }
        }
        return BigDecimal.ZERO;
    }

    private BigDecimal sumStructuredLineValues(String text, String... labels) {
        return Arrays.stream(text.split("\\R"))
                .filter(line -> matchesAnyLabel(normalize(line), labels))
                .map(this::lastAmount)
                .filter(amount -> amount.compareTo(BigDecimal.ZERO) > 0)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal sumValues(String text, Pattern pattern, BigDecimal fallback) {
        Matcher matcher = pattern.matcher(text);
        List<BigDecimal> values = new ArrayList<>();
        while (matcher.find()) {
            values.add(parseAmount(matcher.group(1)));
        }
        if (values.isEmpty()) {
            return fallback;
        }
        return values.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal parseAmount(String value) {
        return new BigDecimal(value.replace(",", ""));
    }

    private BigDecimal lastAmount(String line) {
        Matcher matcher = AMOUNT_PATTERN.matcher(line);
        BigDecimal value = BigDecimal.ZERO;
        while (matcher.find()) {
            value = parseAmount(matcher.group(1));
        }
        return value;
    }

    private boolean matchesAnyLabel(String normalizedLine, String... labels) {
        for (String label : labels) {
            if (normalizedLine.contains(normalize(label))) {
                return true;
            }
        }
        return false;
    }

    private String normalize(String value) {
        return value == null
                ? ""
                : value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", " ").trim();
    }

    private BigDecimal confidenceScore(ExtractedFinancialFields fields, String extractedText) {
        int populatedFields = 0;
        populatedFields += fields.getMonthlyIncome().compareTo(BigDecimal.ZERO) > 0 ? 1 : 0;
        populatedFields += fields.getAverageMonthlyDeposits().compareTo(BigDecimal.ZERO) > 0 ? 1 : 0;
        populatedFields += fields.getExistingDebtPayment().compareTo(BigDecimal.ZERO) > 0 ? 1 : 0;
        populatedFields += fields.getBusinessRevenue().compareTo(BigDecimal.ZERO) > 0 ? 1 : 0;
        populatedFields += fields.getTaxValue().compareTo(BigDecimal.ZERO) > 0 ? 1 : 0;
        populatedFields += fields.getInvoiceTotal().compareTo(BigDecimal.ZERO) > 0 ? 1 : 0;
        BigDecimal fieldCoverage = BigDecimal.valueOf(populatedFields)
                .divide(BigDecimal.valueOf(6), 2, java.math.RoundingMode.HALF_UP);
        BigDecimal textQuality = extractedText.length() >= 100 ? BigDecimal.valueOf(0.25) : BigDecimal.valueOf(0.10);
        return fieldCoverage.add(textQuality).min(BigDecimal.ONE);
    }

    private void deleteWorkingCopy(Path workingCopy) {
        try {
            Files.deleteIfExists(workingCopy);
        } catch (IOException ignored) {
            // Temporary OCR copies are best-effort cleanup.
        }
    }
}
