package com.credbridge.backend.report;

import com.credbridge.backend.application.ApplicationMode;
import com.credbridge.backend.application.ApplicationStatus;
import com.credbridge.backend.financial.EmploymentType;
import com.credbridge.backend.financial.IncomeStability;
import com.credbridge.backend.financial.RepaymentHistory;
import com.credbridge.backend.scoring.RiskLevel;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class ReportResponseDto {

    private Long applicationId;

    private String fullName;

    private ApplicationMode mode;

    private ApplicationStatus status;

    private BigDecimal requestedAmount;

    private Integer tenureMonths;

    private LocalDateTime createdAt;

    private String reviewNotes;

    private Long reviewedByUserId;

    private LocalDateTime reviewedAt;

    private EmploymentType employmentType;

    private BigDecimal monthlyIncome;

    private BigDecimal monthlyExpenses;

    private BigDecimal existingDebtPayment;

    private RepaymentHistory repaymentHistory;

    private IncomeStability incomeStability;

    private Integer score;

    private RiskLevel riskLevel;

    private BigDecimal debtToIncomeRatio;

    private BigDecimal expenseRatio;

    private BigDecimal repaymentCapacity;

    private BigDecimal suggestedLoanLimit;

    private BigDecimal cashFlowStabilityScore;

    private BigDecimal businessHealthScore;

    private List<String> positiveFactors;

    private List<String> riskFactors;

    private List<String> fraudIndicators;

    private String riskExplanation;

    private BigDecimal modelConfidenceScore;

    private BigDecimal defaultRisk;

    private String lendingRecommendation;

    private Integer verifiedDocumentCount;

    private String llmModel;

    private String llmPromptVersion;

    private String llmReasoningSummary;
}
