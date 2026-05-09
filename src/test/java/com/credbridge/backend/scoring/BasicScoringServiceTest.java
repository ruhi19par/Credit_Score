package com.credbridge.backend.scoring;

import static org.assertj.core.api.Assertions.assertThat;

import com.credbridge.backend.application.BasicApplicationRequestDto;
import com.credbridge.backend.financial.EmploymentType;
import com.credbridge.backend.financial.IncomeStability;
import com.credbridge.backend.financial.RepaymentHistory;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class BasicScoringServiceTest {

    private final BasicScoringService scoringService = new BasicScoringService();

    @Test
    void calculatesLowRiskScoreForStrongProfile() {
        ScoreResponseDto score = scoringService.calculate(request(
                "80000.00",
                "25000.00",
                "8000.00",
                "300000.00",
                24,
                RepaymentHistory.EXCELLENT,
                IncomeStability.STABLE
        ));

        assertThat(score.getScore()).isEqualTo(100);
        assertThat(score.getRiskLevel()).isEqualTo(RiskLevel.LOW);
        assertThat(score.getDebtToIncomeRatio()).isEqualByComparingTo("10.00");
        assertThat(score.getExpenseRatio()).isEqualByComparingTo("31.25");
        assertThat(score.getRepaymentCapacity()).isEqualByComparingTo("47000.00");
        assertThat(score.getSuggestedLoanLimit()).isEqualByComparingTo("564000.00");
        assertThat(score.getPositiveFactors()).contains("Stable income", "Healthy repayment history");
        assertThat(score.getRiskFactors()).isEmpty();
    }

    @Test
    void calculatesHighRiskScoreForWeakProfile() {
        ScoreResponseDto score = scoringService.calculate(request(
                "30000.00",
                "24000.00",
                "18000.00",
                "240000.00",
                12,
                RepaymentHistory.POOR,
                IncomeStability.UNSTABLE
        ));

        assertThat(score.getScore()).isEqualTo(15);
        assertThat(score.getRiskLevel()).isEqualTo(RiskLevel.HIGH);
        assertThat(score.getRepaymentCapacity()).isEqualByComparingTo("-12000.00");
        assertThat(score.getSuggestedLoanLimit()).isEqualByComparingTo("0.00");
        assertThat(score.getRiskFactors()).contains(
                "High debt-to-income ratio",
                "High monthly expense ratio",
                "Limited repayment capacity",
                "Poor repayment history",
                "Unstable income"
        );
    }

    private BasicApplicationRequestDto request(
            String monthlyIncome,
            String monthlyExpenses,
            String existingDebtPayment,
            String requestedAmount,
            int tenureMonths,
            RepaymentHistory repaymentHistory,
            IncomeStability incomeStability
    ) {
        BasicApplicationRequestDto request = new BasicApplicationRequestDto();
        request.setFullName("Asha Kumar");
        request.setEmploymentType(EmploymentType.SALARIED);
        request.setMonthlyIncome(new BigDecimal(monthlyIncome));
        request.setMonthlyExpenses(new BigDecimal(monthlyExpenses));
        request.setExistingDebtPayment(new BigDecimal(existingDebtPayment));
        request.setRepaymentHistory(repaymentHistory);
        request.setIncomeStability(incomeStability);
        request.setRequestedAmount(new BigDecimal(requestedAmount));
        request.setTenureMonths(tenureMonths);
        return request;
    }
}
