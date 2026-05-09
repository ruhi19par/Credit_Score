package com.credbridge.backend.application;

import com.credbridge.backend.financial.EmploymentType;
import com.credbridge.backend.financial.IncomeStability;
import com.credbridge.backend.financial.RepaymentHistory;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class BasicApplicationRequestDto {

    @NotBlank
    private String fullName;

    @NotNull
    private EmploymentType employmentType;

    @NotNull
    @DecimalMin("1.00")
    private BigDecimal monthlyIncome;

    @NotNull
    @DecimalMin("0.00")
    private BigDecimal monthlyExpenses;

    @NotNull
    @DecimalMin("0.00")
    private BigDecimal existingDebtPayment;

    @NotNull
    private RepaymentHistory repaymentHistory;

    @NotNull
    private IncomeStability incomeStability;

    @NotNull
    @DecimalMin("1.00")
    private BigDecimal requestedAmount;

    @NotNull
    @Min(1)
    private Integer tenureMonths;
}
