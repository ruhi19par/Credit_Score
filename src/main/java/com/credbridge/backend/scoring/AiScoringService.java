package com.credbridge.backend.scoring;

import com.credbridge.backend.application.LoanApplication;
import com.credbridge.backend.document.ExtractedFinancialFields;
import com.credbridge.backend.financial.FinancialProfile;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class AiScoringService {

    private final LlmScoringClient llmScoringClient;

    public AiScoringService(LlmScoringClient llmScoringClient) {
        this.llmScoringClient = llmScoringClient;
    }

    public ScoringDecision evaluate(
            int score,
            RiskLevel riskLevel,
            LoanApplication application,
            ExtractedFinancialFields fields,
            BigDecimal dtiRatio,
            BigDecimal expenseRatio,
            BigDecimal repaymentCapacity,
            BigDecimal cashFlowStabilityScore,
            BigDecimal businessHealthScore,
            List<String> fraudIndicators
    ) {
        LlmScoringRequest request = toLlmRequest(
                score,
                riskLevel,
                application,
                fields,
                dtiRatio,
                expenseRatio,
                repaymentCapacity,
                cashFlowStabilityScore,
                businessHealthScore,
                fraudIndicators
        );
        return llmScoringClient.evaluate(request)
                .orElseGet(() -> fallbackDecision(score, riskLevel, fields, dtiRatio, expenseRatio, repaymentCapacity));
    }

    private ScoringDecision fallbackDecision(
            int score,
            RiskLevel riskLevel,
            ExtractedFinancialFields fields,
            BigDecimal dtiRatio,
            BigDecimal expenseRatio,
            BigDecimal repaymentCapacity
    ) {
        BigDecimal defaultRisk = BigDecimal.valueOf(100 - score)
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        BigDecimal confidence = fields.getConfidenceScore()
                .multiply(BigDecimal.valueOf(0.70))
                .add(BigDecimal.valueOf(score).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(0.30)))
                .min(BigDecimal.ONE)
                .setScale(2, RoundingMode.HALF_UP);
        String recommendation = switch (riskLevel) {
            case LOW -> "APPROVE";
            case MEDIUM -> repaymentCapacity.compareTo(BigDecimal.ZERO) > 0 ? "MANUAL_REVIEW" : "DECLINE";
            case HIGH -> "DECLINE";
        };
        String explanation = "Verified score " + score
                + " with " + dtiRatio + "% DTI, "
                + expenseRatio + "% expense ratio, and repayment capacity "
                + repaymentCapacity + ". Risk level is " + riskLevel + ".";
        return new ScoringDecision(explanation, confidence, defaultRisk, recommendation, null, null, null, null);
    }

    private LlmScoringRequest toLlmRequest(
            int score,
            RiskLevel riskLevel,
            LoanApplication application,
            ExtractedFinancialFields fields,
            BigDecimal dtiRatio,
            BigDecimal expenseRatio,
            BigDecimal repaymentCapacity,
            BigDecimal cashFlowStabilityScore,
            BigDecimal businessHealthScore,
            List<String> fraudIndicators
    ) {
        FinancialProfile profile = application.getFinancialProfile();
        return new LlmScoringRequest(
                profile == null ? null : profile.getMonthlyIncome(),
                profile == null ? null : profile.getMonthlyExpenses(),
                profile == null ? null : profile.getExistingDebtPayment(),
                fields.getMonthlyIncome(),
                fields.getMonthlyExpenses(),
                fields.getExistingDebtPayment(),
                dtiRatio,
                expenseRatio,
                repaymentCapacity,
                cashFlowStabilityScore,
                businessHealthScore,
                fraudIndicators,
                fields.getConfidenceScore(),
                score,
                riskLevel
        );
    }
}
