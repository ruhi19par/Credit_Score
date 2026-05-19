package com.credbridge.backend.scoring;

import com.credbridge.backend.application.ApplicationMode;
import com.credbridge.backend.application.ApplicationStatus;
import com.credbridge.backend.application.LoanApplication;
import com.credbridge.backend.document.ExtractedFinancialFields;
import com.credbridge.backend.document.ExtractedFinancialFieldsRepository;
import com.credbridge.backend.financial.EmploymentType;
import com.credbridge.backend.financial.FinancialProfile;
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
    private final AiScoringService aiScoringService;
    private final ExtractedFinancialFieldsRepository extractedFinancialFieldsRepository;

    public VerifiedScoringService(
            CreditScoreRepository creditScoreRepository,
            AiScoringService aiScoringService,
            ExtractedFinancialFieldsRepository extractedFinancialFieldsRepository
    ) {
        this.creditScoreRepository = creditScoreRepository;
        this.aiScoringService = aiScoringService;
        this.extractedFinancialFieldsRepository = extractedFinancialFieldsRepository;
    }

    public CreditScore calculateAndSave(LoanApplication application, ExtractedFinancialFields fields) {
        List<ExtractedFinancialFields> extractedFields = extractedFinancialFieldsRepository
                .findByDocumentApplicationId(application.getId());
        if (extractedFields.isEmpty()) {
            extractedFields = List.of(fields);
        }
        ExtractedFinancialFields aggregatedFields = aggregateFields(fields, extractedFields);

        return calculateAndSave(application, aggregatedFields, extractedFields.size());
    }

    private CreditScore calculateAndSave(
            LoanApplication application,
            ExtractedFinancialFields fields,
            int verifiedDocumentCount
    ) {
        BigDecimal dtiRatio = percentage(fields.getExistingDebtPayment(), fields.getMonthlyIncome());
        BigDecimal expenseRatio = percentage(fields.getMonthlyExpenses(), fields.getMonthlyIncome());
        BigDecimal repaymentCapacity = fields.getMonthlyIncome()
                .subtract(fields.getMonthlyExpenses())
                .subtract(fields.getExistingDebtPayment())
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal suggestedLoanLimit = repaymentCapacity.compareTo(BigDecimal.ZERO) <= 0
                ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)
                : repaymentCapacity.multiply(MONTHLY_LIMIT_MULTIPLIER).setScale(2, RoundingMode.HALF_UP);
        BigDecimal cashFlowStabilityScore = calculateCashFlowStabilityScore(fields);
        BigDecimal businessHealthScore = calculateBusinessHealthScore(application, fields, cashFlowStabilityScore);
        List<String> fraudIndicators = fraudIndicators(application, fields);
        int score = calculateScore(
                application,
                dtiRatio,
                expenseRatio,
                repaymentCapacity,
                cashFlowStabilityScore,
                businessHealthScore,
                fraudIndicators
        );
        RiskLevel riskLevel = classifyRiskLevel(score);
        ScoringDecision scoringDecision = aiScoringService.evaluate(
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

        CreditScore creditScore = creditScoreRepository.findByApplicationId(application.getId())
                .orElseGet(CreditScore::new);
        creditScore.setApplication(application);
        creditScore.setScore(score);
        creditScore.setRiskLevel(riskLevel);
        creditScore.setDtiRatio(dtiRatio);
        creditScore.setExpenseRatio(expenseRatio);
        creditScore.setRepaymentCapacity(repaymentCapacity);
        creditScore.setSuggestedLoanLimit(suggestedLoanLimit);
        creditScore.setCashFlowStabilityScore(cashFlowStabilityScore);
        creditScore.setBusinessHealthScore(businessHealthScore);
        creditScore.setVerifiedDocumentCount(verifiedDocumentCount);
        creditScore.setPositiveFactors(String.join("\n", positiveFactors(
                fields,
                dtiRatio,
                expenseRatio,
                repaymentCapacity,
                cashFlowStabilityScore,
                businessHealthScore
        )));
        creditScore.setRiskFactors(String.join("\n", riskFactors(
                fields,
                dtiRatio,
                expenseRatio,
                repaymentCapacity,
                cashFlowStabilityScore,
                fraudIndicators
        )));
        creditScore.setFraudIndicators(String.join("\n", fraudIndicators));
        creditScore.setRiskExplanation(scoringDecision.riskExplanation());
        creditScore.setModelConfidenceScore(scoringDecision.confidenceScore());
        creditScore.setDefaultRisk(scoringDecision.defaultRisk());
        creditScore.setLendingRecommendation(scoringDecision.lendingRecommendation());
        creditScore.setLlmModel(scoringDecision.llmModel());
        creditScore.setLlmPromptVersion(scoringDecision.llmPromptVersion());
        creditScore.setLlmRawResponse(scoringDecision.llmRawResponse());
        creditScore.setLlmReasoningSummary(scoringDecision.llmReasoningSummary());
        creditScore.setCreatedAt(LocalDateTime.now());

        application.setMode(ApplicationMode.VERIFIED);
        application.setStatus(ApplicationStatus.SCORED);

        return creditScoreRepository.save(creditScore);
    }

    private ExtractedFinancialFields aggregateFields(
            ExtractedFinancialFields latestFields,
            List<ExtractedFinancialFields> extractedFields
    ) {
        ExtractedFinancialFields aggregated = new ExtractedFinancialFields();
        aggregated.setDocument(latestFields.getDocument());
        aggregated.setMonthlyIncome(maxPositive(extractedFields, ExtractedFinancialFields::getMonthlyIncome));
        aggregated.setMonthlyExpenses(maxPositive(extractedFields, ExtractedFinancialFields::getMonthlyExpenses));
        aggregated.setExistingDebtPayment(maxPositive(extractedFields, ExtractedFinancialFields::getExistingDebtPayment));
        aggregated.setAverageMonthlyDeposits(averagePositive(extractedFields, ExtractedFinancialFields::getAverageMonthlyDeposits));
        aggregated.setAverageMonthlyWithdrawals(averagePositive(extractedFields, ExtractedFinancialFields::getAverageMonthlyWithdrawals));
        aggregated.setBusinessRevenue(maxPositive(extractedFields, ExtractedFinancialFields::getBusinessRevenue));
        aggregated.setTaxValue(sumPositive(extractedFields, ExtractedFinancialFields::getTaxValue));
        aggregated.setInvoiceTotal(sumPositive(extractedFields, ExtractedFinancialFields::getInvoiceTotal));
        aggregated.setRevenueConsistencyScore(averagePositive(extractedFields, ExtractedFinancialFields::getRevenueConsistencyScore));
        aggregated.setConfidenceScore(averagePositive(extractedFields, ExtractedFinancialFields::getConfidenceScore));
        aggregated.setExtractedText(extractedFields.stream()
                .map(ExtractedFinancialFields::getExtractedText)
                .filter(text -> text != null && !text.isBlank())
                .reduce((left, right) -> left + "\n--- document boundary ---\n" + right)
                .orElse(""));
        aggregated.setCreatedAt(LocalDateTime.now());
        return aggregated;
    }

    private BigDecimal maxPositive(
            List<ExtractedFinancialFields> fields,
            java.util.function.Function<ExtractedFinancialFields, BigDecimal> extractor
    ) {
        return fields.stream()
                .map(extractor)
                .filter(value -> value != null && value.compareTo(BigDecimal.ZERO) > 0)
                .max(BigDecimal::compareTo)
                .orElse(BigDecimal.ZERO)
                .setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal sumPositive(
            List<ExtractedFinancialFields> fields,
            java.util.function.Function<ExtractedFinancialFields, BigDecimal> extractor
    ) {
        return fields.stream()
                .map(extractor)
                .filter(value -> value != null && value.compareTo(BigDecimal.ZERO) > 0)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal averagePositive(
            List<ExtractedFinancialFields> fields,
            java.util.function.Function<ExtractedFinancialFields, BigDecimal> extractor
    ) {
        List<BigDecimal> values = fields.stream()
                .map(extractor)
                .filter(value -> value != null && value.compareTo(BigDecimal.ZERO) > 0)
                .toList();
        if (values.isEmpty()) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return values.stream()
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(values.size()), 2, RoundingMode.HALF_UP);
    }

    private int calculateScore(
            LoanApplication application,
            BigDecimal dtiRatio,
            BigDecimal expenseRatio,
            BigDecimal repaymentCapacity,
            BigDecimal cashFlowStabilityScore,
            BigDecimal businessHealthScore,
            List<String> fraudIndicators
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
        score += cashFlowStabilityScore.compareTo(BigDecimal.valueOf(75)) >= 0 ? 10
                : cashFlowStabilityScore.compareTo(BigDecimal.valueOf(50)) >= 0 ? 6 : 2;
        score += businessHealthScore.compareTo(BigDecimal.valueOf(75)) >= 0 ? 10
                : businessHealthScore.compareTo(BigDecimal.valueOf(50)) >= 0 ? 6 : 2;
        score -= fraudIndicators.size() * 8;

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
            BigDecimal repaymentCapacity,
            BigDecimal cashFlowStabilityScore,
            BigDecimal businessHealthScore
    ) {
        return List.of(
                "Verified document data processed",
                fields.getConfidenceScore().compareTo(BigDecimal.valueOf(0.50)) >= 0
                        ? "OCR confidence meets configured threshold"
                        : "OCR confidence is available for review",
                dtiRatio.compareTo(BigDecimal.valueOf(30)) <= 0
                        ? "Verified debt burden is manageable"
                        : "Verified debt burden was calculated",
                expenseRatio.compareTo(BigDecimal.valueOf(50)) <= 0
                        ? "Verified expenses are controlled"
                        : "Verified expenses were calculated",
                repaymentCapacity.compareTo(BigDecimal.ZERO) > 0
                        ? "Verified repayment capacity is positive"
                        : "Verified repayment capacity requires review",
                cashFlowStabilityScore.compareTo(BigDecimal.valueOf(50)) >= 0
                        ? "Verified cash flow stability is acceptable"
                        : "Verified cash flow stability requires review",
                businessHealthScore.compareTo(BigDecimal.valueOf(50)) >= 0
                        ? "Business health indicators are acceptable"
                        : "Business health indicators require review"
        );
    }

    private List<String> riskFactors(
            ExtractedFinancialFields fields,
            BigDecimal dtiRatio,
            BigDecimal expenseRatio,
            BigDecimal repaymentCapacity,
            BigDecimal cashFlowStabilityScore,
            List<String> fraudIndicators
    ) {
        java.util.ArrayList<String> values = new java.util.ArrayList<>();
        if (fields.getConfidenceScore().compareTo(BigDecimal.valueOf(0.70)) < 0) {
            values.add("OCR confidence is below review threshold");
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
        if (cashFlowStabilityScore.compareTo(BigDecimal.valueOf(50)) < 0) {
            values.add("Cash flow stability is weak");
        }
        values.addAll(fraudIndicators);
        return values;
    }

    private List<String> fraudIndicators(LoanApplication application, ExtractedFinancialFields fields) {
        java.util.ArrayList<String> values = new java.util.ArrayList<>();
        FinancialProfile profile = application.getFinancialProfile();
        if (profile == null) {
            return values;
        }

        addMismatch(values, "income", profile.getMonthlyIncome(), fields.getMonthlyIncome());
        addMismatch(values, "expenses", profile.getMonthlyExpenses(), fields.getMonthlyExpenses());
        addMismatch(values, "debt payment", profile.getExistingDebtPayment(), fields.getExistingDebtPayment());
        return values;
    }

    private void addMismatch(
            List<String> values,
            String label,
            BigDecimal declaredValue,
            BigDecimal verifiedValue
    ) {
        if (declaredValue == null || verifiedValue == null || declaredValue.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }

        BigDecimal differencePercent = verifiedValue
                .subtract(declaredValue)
                .abs()
                .multiply(ONE_HUNDRED)
                .divide(declaredValue, 2, RoundingMode.HALF_UP);
        if (differencePercent.compareTo(BigDecimal.valueOf(20)) > 0) {
            values.add("Declared " + label + " differs from verified " + label + " by " + differencePercent + "%");
        }
    }

    private BigDecimal calculateCashFlowStabilityScore(ExtractedFinancialFields fields) {
        BigDecimal deposits = fields.getAverageMonthlyDeposits();
        BigDecimal withdrawals = fields.getAverageMonthlyWithdrawals();
        if (deposits == null || deposits.compareTo(BigDecimal.ZERO) <= 0 || withdrawals == null) {
            return BigDecimal.valueOf(50).setScale(2, RoundingMode.HALF_UP);
        }

        BigDecimal retainedCashRatio = deposits
                .subtract(withdrawals)
                .multiply(ONE_HUNDRED)
                .divide(deposits, 2, RoundingMode.HALF_UP);
        return retainedCashRatio
                .add(BigDecimal.valueOf(60))
                .max(BigDecimal.ZERO)
                .min(ONE_HUNDRED)
                .setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal calculateBusinessHealthScore(
            LoanApplication application,
            ExtractedFinancialFields fields,
            BigDecimal cashFlowStabilityScore
    ) {
        BigDecimal revenueConsistency = fields.getRevenueConsistencyScore() == null
                ? BigDecimal.valueOf(60)
                : fields.getRevenueConsistencyScore();
        BigDecimal baseScore = cashFlowStabilityScore.add(revenueConsistency).divide(BigDecimal.valueOf(2), 2, RoundingMode.HALF_UP);
        FinancialProfile profile = application.getFinancialProfile();
        if (profile != null && profile.getEmploymentType() == EmploymentType.BUSINESS) {
            return baseScore;
        }
        return baseScore.min(BigDecimal.valueOf(70)).setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal percentage(BigDecimal value, BigDecimal total) {
        if (total.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }

        return value.multiply(ONE_HUNDRED).divide(total, 2, RoundingMode.HALF_UP);
    }
}
