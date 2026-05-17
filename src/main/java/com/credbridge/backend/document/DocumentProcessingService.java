package com.credbridge.backend.document;

import com.credbridge.backend.scoring.VerifiedScoringService;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DocumentProcessingService {

    private static final Pattern INCOME_PATTERN = Pattern.compile("(?i)(?:monthly\\s*)?income\\D+(\\d+(?:\\.\\d+)?)");
    private static final Pattern EXPENSE_PATTERN = Pattern.compile("(?i)(?:monthly\\s*)?expenses?\\D+(\\d+(?:\\.\\d+)?)");
    private static final Pattern DEBT_PATTERN = Pattern.compile("(?i)(?:debt|emi|loan\\s*payment)\\D+(\\d+(?:\\.\\d+)?)");

    private final DocumentRepository documentRepository;
    private final ExtractedFinancialFieldsRepository extractedFinancialFieldsRepository;
    private final OcrPlaceholderService ocrPlaceholderService;
    private final VerifiedScoringService verifiedScoringService;

    public DocumentProcessingService(
            DocumentRepository documentRepository,
            ExtractedFinancialFieldsRepository extractedFinancialFieldsRepository,
            OcrPlaceholderService ocrPlaceholderService,
            VerifiedScoringService verifiedScoringService
    ) {
        this.documentRepository = documentRepository;
        this.extractedFinancialFieldsRepository = extractedFinancialFieldsRepository;
        this.ocrPlaceholderService = ocrPlaceholderService;
        this.verifiedScoringService = verifiedScoringService;
    }

    @Transactional
    public void process(Document document) {
        document.setStatus(DocumentStatus.PROCESSING);
        documentRepository.save(document);

        try {
            String extractedText = ocrPlaceholderService.extractText(document);
            ExtractedFinancialFields fields = new ExtractedFinancialFields();
            fields.setDocument(document);
            fields.setExtractedText(extractedText);
            fields.setMonthlyIncome(extractValue(extractedText, INCOME_PATTERN, BigDecimal.valueOf(60000)));
            fields.setMonthlyExpenses(extractValue(extractedText, EXPENSE_PATTERN, BigDecimal.valueOf(25000)));
            fields.setExistingDebtPayment(extractValue(extractedText, DEBT_PATTERN, BigDecimal.valueOf(5000)));
            fields.setConfidenceScore(BigDecimal.valueOf(0.60));
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

    private BigDecimal extractValue(String text, Pattern pattern, BigDecimal fallback) {
        Matcher matcher = pattern.matcher(text);
        if (!matcher.find()) {
            return fallback;
        }
        return new BigDecimal(matcher.group(1));
    }
}
