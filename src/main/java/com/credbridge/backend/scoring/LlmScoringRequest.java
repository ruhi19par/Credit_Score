package com.credbridge.backend.scoring;

import java.math.BigDecimal;
import java.util.List;

public record LlmScoringRequest(
        BigDecimal declaredIncome,
        BigDecimal declaredExpenses,
        BigDecimal declaredDebtPayment,
        BigDecimal verifiedIncome,
        BigDecimal verifiedExpenses,
        BigDecimal verifiedDebtPayment,
        BigDecimal dtiRatio,
        BigDecimal expenseRatio,
        BigDecimal repaymentCapacity,
        BigDecimal cashFlowStabilityScore,
        BigDecimal businessHealthScore,
        List<String> fraudIndicators,
        BigDecimal ocrConfidence,
        int currentCalculatedScore,
        RiskLevel riskLevel
) {
}
