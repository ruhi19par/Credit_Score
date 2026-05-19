package com.credbridge.backend.privacy;

import java.time.LocalDateTime;

public record AuditEventResponseDto(
        Long id,
        Long userId,
        Long applicationId,
        String action,
        String details,
        LocalDateTime createdAt
) {

    public static AuditEventResponseDto from(AuditEvent auditEvent) {
        return new AuditEventResponseDto(
                auditEvent.getId(),
                auditEvent.getUser() == null ? null : auditEvent.getUser().getId(),
                auditEvent.getApplication() == null ? null : auditEvent.getApplication().getId(),
                auditEvent.getAction(),
                auditEvent.getDetails(),
                auditEvent.getCreatedAt()
        );
    }
}
