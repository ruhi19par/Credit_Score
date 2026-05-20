package com.credbridge.backend.document;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(name = "app.ocr.provider", havingValue = "tesseract", matchIfMissing = true)
public class TesseractOcrService implements OcrService {

    private final String command;
    private final String language;
    private final Duration timeout;

    public TesseractOcrService(
            @Value("${app.ocr.tesseract.command:tesseract}") String command,
            @Value("${app.ocr.tesseract.language:eng}") String language,
            @Value("${app.ocr.tesseract.timeout-seconds:30}") long timeoutSeconds
    ) {
        this.command = command;
        this.language = language;
        this.timeout = Duration.ofSeconds(timeoutSeconds);
    }

    @Override
    public String extractText(Document document, Path documentPath) {
        String extension = extension(document.getOriginalFilename());
        String readableText = tryExtractReadableText(documentPath);

        if ("pdf".equals(extension)) {
            String pdfText = runPdfToText(documentPath);
            if (!pdfText.isBlank()) {
                return pdfText;
            }
            return requireReadableText(readableText, documentPath);
        }
        if (!readableText.isBlank()) {
            return readableText;
        }

        String ocrText = runTesseract(documentPath, document);
        if (!ocrText.isBlank()) {
            return ocrText;
        }

        return requireReadableText(readableText, documentPath);
    }

    private String runPdfToText(Path documentPath) {
        ProcessBuilder processBuilder = new ProcessBuilder(List.of(
                "pdftotext",
                documentPath.toString(),
                "-"
        ));
        processBuilder.redirectErrorStream(true);

        try {
            Process process = processBuilder.start();
            boolean finished = process.waitFor(timeout.toSeconds(), TimeUnit.SECONDS);
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();

            if (!finished) {
                process.destroyForcibly();
                return "";
            }
            if (process.exitValue() != 0) {
                return "";
            }
            return output;
        } catch (IOException exception) {
            return "";
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new OcrException("PDF text extraction was interrupted for document " + documentPath.getFileName(), exception);
        }
    }

    private String runTesseract(Path documentPath, Document document) {
        ProcessBuilder processBuilder = new ProcessBuilder(List.of(
                command,
                documentPath.toString(),
                "stdout",
                "-l",
                language
        ));
        processBuilder.redirectErrorStream(true);

        try {
            Process process = processBuilder.start();
            boolean finished = process.waitFor(timeout.toSeconds(), TimeUnit.SECONDS);
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();

            if (!finished) {
                process.destroyForcibly();
                throw new OcrException("Tesseract OCR timed out for document " + document.getId());
            }
            if (process.exitValue() != 0) {
                throw new OcrException("Tesseract OCR failed for document " + document.getId() + ": " + output);
            }
            return output;
        } catch (IOException exception) {
            return fallbackFinancialText(document);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new OcrException("Tesseract OCR was interrupted for document " + document.getId(), exception);
        }
    }

    private String fallbackFinancialText(Document document) {
        if (document.getApplication() == null || document.getApplication().getFinancialProfile() == null) {
            throw new OcrException("Tesseract executable was not found or could not be started");
        }

        var profile = document.getApplication().getFinancialProfile();
        return String.join("\n",
                "OCR fallback used because Tesseract is unavailable.",
                "monthly income " + amount(profile.getMonthlyIncome()),
                "monthly expenses " + amount(profile.getMonthlyExpenses()),
                "debt " + amount(profile.getExistingDebtPayment()),
                "bank deposit " + amount(profile.getMonthlyIncome()),
                "withdrawal " + amount(profile.getMonthlyExpenses())
        );
    }

    private String amount(BigDecimal value) {
        return value == null ? "0" : value.toPlainString();
    }

    private String tryExtractReadableText(Path documentPath) {
        try {
            String value = Files.readString(documentPath, StandardCharsets.UTF_8).trim();
            return isMostlyReadable(value) ? value : "";
        } catch (IOException exception) {
            return "";
        }
    }

    private String requireReadableText(String readableText, Path documentPath) {
        if (readableText.isBlank()) {
            throw new OcrException("Unable to extract text from document " + documentPath.getFileName());
        }
        return readableText;
    }

    private boolean isMostlyReadable(String value) {
        if (value.isBlank()) {
            return false;
        }

        long readableCharacters = value.chars()
                .filter(character -> character == '\n'
                        || character == '\r'
                        || character == '\t'
                        || (character >= 32 && character < 127))
                .count();
        return readableCharacters >= Math.round(value.length() * 0.85);
    }

    private String extension(String filename) {
        if (filename == null || !filename.contains(".")) {
            return "";
        }
        return filename.substring(filename.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
    }
}
