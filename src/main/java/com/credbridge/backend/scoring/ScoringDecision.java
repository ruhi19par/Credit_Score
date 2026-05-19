package com.credbridge.backend.scoring;

import java.math.BigDecimal;

public record ScoringDecision(
        String riskExplanation,
        BigDecimal confidenceScore,
        BigDecimal defaultRisk,
        String lendingRecommendation,
        String llmModel,
        String llmPromptVersion,
        String llmRawResponse,
        String llmReasoningSummary
) {
}
