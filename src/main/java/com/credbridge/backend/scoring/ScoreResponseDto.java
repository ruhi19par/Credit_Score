package com.credbridge.backend.scoring;

import java.math.BigDecimal;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class ScoreResponseDto {

    private Long applicationId;

    private Integer score;

    private RiskLevel riskLevel;

    private BigDecimal debtToIncomeRatio;

    private BigDecimal expenseRatio;

    private BigDecimal repaymentCapacity;

    private BigDecimal suggestedLoanLimit;

    private List<String> positiveFactors;

    private List<String> riskFactors;
}
