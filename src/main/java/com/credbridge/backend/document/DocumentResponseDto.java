package com.credbridge.backend.document;

import java.time.LocalDateTime;

public record DocumentResponseDto(
        Long id,
        Long applicationId,
        DocumentType documentType,
        String originalFilename,
        String storedFilePath,
        DocumentStatus status,
        LocalDateTime createdAt
) {
}
