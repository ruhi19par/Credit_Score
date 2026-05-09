package com.credbridge.backend.financial;

import com.credbridge.backend.application.LoanApplication;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "financial_profiles")
public class FinancialProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne
    @JoinColumn(name = "application_id", nullable = false)
    private LoanApplication application;

    @Enumerated(EnumType.STRING)
    private EmploymentType employmentType;

    private BigDecimal monthlyIncome;

    private BigDecimal monthlyExpenses;

    private BigDecimal existingDebtPayment;

    @Enumerated(EnumType.STRING)
    private RepaymentHistory repaymentHistory;

    @Enumerated(EnumType.STRING)
    private IncomeStability incomeStability;
}
