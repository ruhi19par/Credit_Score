package com.credbridge.backend.admin;

import com.credbridge.backend.application.ApplicationStatus;
import com.credbridge.backend.scoring.RiskLevel;
import java.math.BigDecimal;
import java.util.Map;

public record AdminOverviewResponseDto(
        long totalApplications,
        long totalUsers,
        long totalScores,
        BigDecimal totalRequestedAmount,
        BigDecimal averageScore,
        Map<ApplicationStatus, Long> applicationsByStatus,
        Map<RiskLevel, Long> scoresByRiskLevel
) {
}
