package com.credbridge.backend.scoring;

import com.credbridge.backend.application.ApplicationMode;
import com.credbridge.backend.application.ApplicationStatus;
import com.credbridge.backend.application.LoanApplication;
import com.credbridge.backend.document.ExtractedFinancialFields;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class VerifiedScoringService {

    private static final BigDecimal ONE_HUNDRED = BigDecimal.valueOf(100);
    private static final BigDecimal MONTHLY_LIMIT_MULTIPLIER = BigDecimal.valueOf(12);

    private final CreditScoreRepository creditScoreRepository;

    public VerifiedScoringService(CreditScoreRepository creditScoreRepository) {
        this.creditScoreRepository = creditScoreRepository;
    }

    public CreditScore calculateAndSave(LoanApplication application, ExtractedFinancialFields fields) {
        BigDecimal dtiRatio = percentage(fields.getExistingDebtPayment(), fields.getMonthlyIncome());
        BigDecimal expenseRatio = percentage(fields.getMonthlyExpenses(), fields.getMonthlyIncome());
        BigDecimal repaymentCapacity = fields.getMonthlyIncome()
                .subtract(fields.getMonthlyExpenses())
                .subtract(fields.getExistingDebtPayment())
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal suggestedLoanLimit = repaymentCapacity.compareTo(BigDecimal.ZERO) <= 0
                ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)
                : repaymentCapacity.multiply(MONTHLY_LIMIT_MULTIPLIER).setScale(2, RoundingMode.HALF_UP);
        int score = calculateScore(application, dtiRatio, expenseRatio, repaymentCapacity);
        RiskLevel riskLevel = classifyRiskLevel(score);

        CreditScore creditScore = creditScoreRepository.findByApplicationId(application.getId())
                .orElseGet(CreditScore::new);
        creditScore.setApplication(application);
        creditScore.setScore(score);
        creditScore.setRiskLevel(riskLevel);
        creditScore.setDtiRatio(dtiRatio);
        creditScore.setExpenseRatio(expenseRatio);
        creditScore.setRepaymentCapacity(repaymentCapacity);
        creditScore.setSuggestedLoanLimit(suggestedLoanLimit);
        creditScore.setPositiveFactors(String.join("\n", positiveFactors(fields, dtiRatio, expenseRatio, repaymentCapacity)));
        creditScore.setRiskFactors(String.join("\n", riskFactors(fields, dtiRatio, expenseRatio, repaymentCapacity)));
        creditScore.setCreatedAt(LocalDateTime.now());

        application.setMode(ApplicationMode.VERIFIED);
        application.setStatus(ApplicationStatus.SCORED);

        return creditScoreRepository.save(creditScore);
    }

    private int calculateScore(
            LoanApplication application,
            BigDecimal dtiRatio,
            BigDecimal expenseRatio,
            BigDecimal repaymentCapacity
    ) {
        int score = 10;
        score += dtiRatio.compareTo(BigDecimal.valueOf(30)) <= 0 ? 25
                : dtiRatio.compareTo(BigDecimal.valueOf(50)) <= 0 ? 15 : 5;
        score += expenseRatio.compareTo(BigDecimal.valueOf(50)) <= 0 ? 25
                : expenseRatio.compareTo(BigDecimal.valueOf(70)) <= 0 ? 12 : 5;

        BigDecimal estimatedEmi = application.getRequestedAmount()
                .divide(BigDecimal.valueOf(application.getTenureMonths()), 2, RoundingMode.HALF_UP);
        score += repaymentCapacity.compareTo(estimatedEmi.multiply(BigDecimal.valueOf(1.5))) >= 0 ? 30
                : repaymentCapacity.compareTo(estimatedEmi) >= 0 ? 20
                : repaymentCapacity.compareTo(estimatedEmi.multiply(BigDecimal.valueOf(0.5))) >= 0 ? 10 : 2;

        return Math.max(0, Math.min(score, 100));
    }

    private RiskLevel classifyRiskLevel(int score) {
        if (score >= 80) {
            return RiskLevel.LOW;
        }
        if (score >= 60) {
            return RiskLevel.MEDIUM;
        }
        return RiskLevel.HIGH;
    }

    private List<String> positiveFactors(
            ExtractedFinancialFields fields,
            BigDecimal dtiRatio,
            BigDecimal expenseRatio,
            BigDecimal repaymentCapacity
    ) {
        return List.of(
                "Verified document data processed",
                fields.getConfidenceScore().compareTo(BigDecimal.valueOf(0.50)) >= 0
                        ? "OCR confidence meets placeholder threshold"
                        : "OCR confidence is available for review",
                dtiRatio.compareTo(BigDecimal.valueOf(30)) <= 0
                        ? "Verified debt burden is manageable"
                        : "Verified debt burden was calculated",
                expenseRatio.compareTo(BigDecimal.valueOf(50)) <= 0
                        ? "Verified expenses are controlled"
                        : "Verified expenses were calculated",
                repaymentCapacity.compareTo(BigDecimal.ZERO) > 0
                        ? "Verified repayment capacity is positive"
                        : "Verified repayment capacity requires review"
        );
    }

    private List<String> riskFactors(
            ExtractedFinancialFields fields,
            BigDecimal dtiRatio,
            BigDecimal expenseRatio,
            BigDecimal repaymentCapacity
    ) {
        java.util.ArrayList<String> values = new java.util.ArrayList<>();
        if (fields.getConfidenceScore().compareTo(BigDecimal.valueOf(0.70)) < 0) {
            values.add("OCR confidence is placeholder-level");
        }
        if (dtiRatio.compareTo(BigDecimal.valueOf(50)) > 0) {
            values.add("Verified debt-to-income ratio is high");
        }
        if (expenseRatio.compareTo(BigDecimal.valueOf(70)) > 0) {
            values.add("Verified expense ratio is high");
        }
        if (repaymentCapacity.compareTo(BigDecimal.ZERO) <= 0) {
            values.add("Verified repayment capacity is limited");
        }
        return values;
    }

    private BigDecimal percentage(BigDecimal value, BigDecimal total) {
        if (total.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }

        return value.multiply(ONE_HUNDRED).divide(total, 2, RoundingMode.HALF_UP);
    }
}
