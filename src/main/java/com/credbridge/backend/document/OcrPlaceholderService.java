package com.credbridge.backend.document;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.stereotype.Service;

@Service
public class OcrPlaceholderService {

    public String extractText(Document document) {
        try {
            byte[] bytes = Files.readAllBytes(Path.of(document.getStoredFilePath()));
            String text = new String(bytes, StandardCharsets.UTF_8).trim();
            if (!text.isBlank()) {
                return text;
            }
        } catch (IOException ignored) {
            // Placeholder OCR should not fail the pipeline when a binary file cannot be read as text.
        }

        return "Placeholder OCR text for " + document.getDocumentType() + " document "
                + document.getOriginalFilename();
    }
}
