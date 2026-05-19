package com.credbridge.backend.document;

import java.nio.file.Path;

public interface OcrService {

    String extractText(Document document, Path documentPath);
}
