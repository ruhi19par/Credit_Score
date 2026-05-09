package com.credbridge.backend.scoring;

import com.credbridge.backend.application.BasicApplicationRequestDto;
import com.credbridge.backend.financial.IncomeStability;
import com.credbridge.backend.financial.RepaymentHistory;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class BasicScoringService {

    private static final BigDecimal ONE_HUNDRED = BigDecimal.valueOf(100);
    private static final BigDecimal MONTHLY_LIMIT_MULTIPLIER = BigDecimal.valueOf(12);

    public ScoreResponseDto calculate(BasicApplicationRequestDto request) {
        BigDecimal dtiRatio = calculateDebtToIncomeRatio(request);
        BigDecimal expenseRatio = calculateExpenseRatio(request);
        BigDecimal repaymentCapacity = calculateRepaymentCapacity(request);
        BigDecimal suggestedLoanLimit = calculateSuggestedLoanLimit(repaymentCapacity);
        int score = calculateScore(request, dtiRatio, expenseRatio, repaymentCapacity);
        RiskLevel riskLevel = classifyRiskLevel(score);

        return new ScoreResponseDto(
                null,
                score,
                riskLevel,
                dtiRatio,
                expenseRatio,
                repaymentCapacity,
                suggestedLoanLimit,
                generatePositiveFactors(request, dtiRatio, expenseRatio, repaymentCapacity),
                generateRiskFactors(request, dtiRatio, expenseRatio, repaymentCapacity)
        );
    }

    public BigDecimal calculateDebtToIncomeRatio(BasicApplicationRequestDto request) {
        return percentage(request.getExistingDebtPayment(), request.getMonthlyIncome());
    }

    public BigDecimal calculateExpenseRatio(BasicApplicationRequestDto request) {
        return percentage(request.getMonthlyExpenses(), request.getMonthlyIncome());
    }

    public BigDecimal calculateRepaymentCapacity(BasicApplicationRequestDto request) {
        return request.getMonthlyIncome()
                .subtract(request.getMonthlyExpenses())
                .subtract(request.getExistingDebtPayment())
                .setScale(2, RoundingMode.HALF_UP);
    }

    public BigDecimal calculateSuggestedLoanLimit(BigDecimal repaymentCapacity) {
        if (repaymentCapacity.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }

        return repaymentCapacity
                .multiply(MONTHLY_LIMIT_MULTIPLIER)
                .setScale(2, RoundingMode.HALF_UP);
    }

    public int calculateScore(
            BasicApplicationRequestDto request,
            BigDecimal dtiRatio,
            BigDecimal expenseRatio,
            BigDecimal repaymentCapacity
    ) {
        int score = 0;
        score += scoreDebtToIncomeRatio(dtiRatio);
        score += scoreExpenseRatio(expenseRatio);
        score += scoreRepaymentCapacity(request, repaymentCapacity);
        score += scoreRepaymentHistory(request.getRepaymentHistory());
        score += scoreIncomeStability(request.getIncomeStability());

        return Math.max(0, Math.min(score, 100));
    }

    public RiskLevel classifyRiskLevel(int score) {
        if (score >= 80) {
            return RiskLevel.LOW;
        }
        if (score >= 60) {
            return RiskLevel.MEDIUM;
        }
        return RiskLevel.HIGH;
    }

    public List<String> generatePositiveFactors(
            BasicApplicationRequestDto request,
            BigDecimal dtiRatio,
            BigDecimal expenseRatio,
            BigDecimal repaymentCapacity
    ) {
        List<String> factors = new ArrayList<>();

        if (request.getIncomeStability() == IncomeStability.STABLE) {
            factors.add("Stable income");
        }
        if (dtiRatio.compareTo(BigDecimal.valueOf(30)) <= 0) {
            factors.add("Manageable debt burden");
        }
        if (expenseRatio.compareTo(BigDecimal.valueOf(50)) <= 0) {
            factors.add("Controlled monthly expenses");
        }
        if (repaymentCapacity.compareTo(BigDecimal.ZERO) > 0) {
            factors.add("Positive repayment capacity");
        }
        if (request.getRepaymentHistory() == RepaymentHistory.EXCELLENT
                || request.getRepaymentHistory() == RepaymentHistory.GOOD) {
            factors.add("Healthy repayment history");
        }

        return factors;
    }

    public List<String> generateRiskFactors(
            BasicApplicationRequestDto request,
            BigDecimal dtiRatio,
            BigDecimal expenseRatio,
            BigDecimal repaymentCapacity
    ) {
        List<String> factors = new ArrayList<>();

        if (dtiRatio.compareTo(BigDecimal.valueOf(50)) > 0) {
            factors.add("High debt-to-income ratio");
        }
        if (expenseRatio.compareTo(BigDecimal.valueOf(70)) > 0) {
            factors.add("High monthly expense ratio");
        } else if (expenseRatio.compareTo(BigDecimal.valueOf(50)) > 0) {
            factors.add("Moderate expense ratio");
        }
        if (repaymentCapacity.compareTo(BigDecimal.ZERO) <= 0) {
            factors.add("Limited repayment capacity");
        }
        if (request.getRepaymentHistory() == RepaymentHistory.POOR) {
            factors.add("Poor repayment history");
        }
        if (request.getIncomeStability() == IncomeStability.UNSTABLE) {
            factors.add("Unstable income");
        }

        return factors;
    }

    private BigDecimal percentage(BigDecimal value, BigDecimal total) {
        if (total.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }

        return value
                .multiply(ONE_HUNDRED)
                .divide(total, 2, RoundingMode.HALF_UP);
    }

    private int scoreDebtToIncomeRatio(BigDecimal dtiRatio) {
        if (dtiRatio.compareTo(BigDecimal.valueOf(20)) <= 0) {
            return 25;
        }
        if (dtiRatio.compareTo(BigDecimal.valueOf(35)) <= 0) {
            return 20;
        }
        if (dtiRatio.compareTo(BigDecimal.valueOf(50)) <= 0) {
            return 12;
        }
        return 5;
    }

    private int scoreExpenseRatio(BigDecimal expenseRatio) {
        if (expenseRatio.compareTo(BigDecimal.valueOf(40)) <= 0) {
            return 20;
        }
        if (expenseRatio.compareTo(BigDecimal.valueOf(55)) <= 0) {
            return 15;
        }
        if (expenseRatio.compareTo(BigDecimal.valueOf(70)) <= 0) {
            return 8;
        }
        return 3;
    }

    private int scoreRepaymentCapacity(BasicApplicationRequestDto request, BigDecimal repaymentCapacity) {
        BigDecimal estimatedEmi = request.getRequestedAmount()
                .divide(BigDecimal.valueOf(request.getTenureMonths()), 2, RoundingMode.HALF_UP);

        if (repaymentCapacity.compareTo(estimatedEmi.multiply(BigDecimal.valueOf(1.5))) >= 0) {
            return 25;
        }
        if (repaymentCapacity.compareTo(estimatedEmi) >= 0) {
            return 18;
        }
        if (repaymentCapacity.compareTo(estimatedEmi.multiply(BigDecimal.valueOf(0.5))) >= 0) {
            return 10;
        }
        return 2;
    }

    private int scoreRepaymentHistory(RepaymentHistory repaymentHistory) {
        return switch (repaymentHistory) {
            case EXCELLENT -> 20;
            case GOOD -> 16;
            case AVERAGE -> 10;
            case POOR -> 3;
            case NONE -> 8;
        };
    }

    private int scoreIncomeStability(IncomeStability incomeStability) {
        return switch (incomeStability) {
            case STABLE -> 10;
            case MODERATE -> 6;
            case UNSTABLE -> 2;
        };
    }
}
