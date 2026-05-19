package com.credbridge.backend.privacy;

import java.time.LocalDateTime;

public record ConsentResponseDto(
        Long id,
        Long applicationId,
        String purpose,
        Boolean accepted,
        LocalDateTime createdAt
) {

    public static ConsentResponseDto from(ConsentRecord consentRecord) {
        return new ConsentResponseDto(
                consentRecord.getId(),
                consentRecord.getApplication().getId(),
                consentRecord.getPurpose(),
                consentRecord.getAccepted(),
                consentRecord.getCreatedAt()
        );
    }
}
