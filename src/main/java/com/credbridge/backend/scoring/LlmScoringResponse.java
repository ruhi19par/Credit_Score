package com.credbridge.backend.scoring;

import java.math.BigDecimal;
import java.util.List;

public record LlmScoringResponse(
        String riskExplanation,
        BigDecimal confidenceScore,
        BigDecimal defaultRisk,
        String lendingRecommendation,
        List<String> additionalRiskFactors,
        List<String> positiveFactors
) {
}
