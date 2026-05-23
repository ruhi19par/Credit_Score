package com.credbridge.backend.document;

import java.time.OffsetDateTime;

public record DocumentResponseDto(
        Long id,
        Long applicationId,
        DocumentType documentType,
        String originalFilename,
        DocumentStatus status,
        OffsetDateTime createdAt
) {
}
