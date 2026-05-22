package com.credbridge.backend.admin;

import com.credbridge.backend.application.ApplicationMode;
import com.credbridge.backend.application.ApplicationStatus;
import com.credbridge.backend.scoring.RiskLevel;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public record AdminApplicationResponseDto(
        Long id,
        String fullName,
        ApplicationMode mode,
        ApplicationStatus status,
        BigDecimal requestedAmount,
        Integer tenureMonths,
        LocalDateTime createdAt,
        Long userId,
        String userEmail,
        Integer score,
        RiskLevel riskLevel,
        String riskExplanation,
        BigDecimal modelConfidenceScore,
        BigDecimal defaultRisk,
        String lendingRecommendation,
        BigDecimal cashFlowStabilityScore,
        BigDecimal businessHealthScore,
        String fraudIndicators,
        String reviewNotes,
        Long reviewedByUserId,
        LocalDateTime reviewedAt
) {
}
