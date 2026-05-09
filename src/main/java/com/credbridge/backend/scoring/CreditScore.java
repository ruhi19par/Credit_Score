package com.credbridge.backend.scoring;

import com.credbridge.backend.application.LoanApplication;
import jakarta.persistence.Column;
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
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "credit_scores")
public class CreditScore {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne
    @JoinColumn(name = "application_id", nullable = false)
    private LoanApplication application;

    private Integer score;

    @Enumerated(EnumType.STRING)
    private RiskLevel riskLevel;

    private BigDecimal dtiRatio;

    private BigDecimal expenseRatio;

    private BigDecimal repaymentCapacity;

    private BigDecimal suggestedLoanLimit;

    @Column(length = 1000)
    private String positiveFactors;

    @Column(length = 1000)
    private String riskFactors;

    private LocalDateTime createdAt;
}
