package com.credbridge.backend.document;

import java.nio.file.Path;

public record ValidatedDocumentFile(Path path, String contentType, long size) {
}
