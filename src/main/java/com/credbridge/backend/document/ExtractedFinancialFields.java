package com.credbridge.backend.document;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
@Table(name = "extracted_financial_fields")
public class ExtractedFinancialFields {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne
    @JoinColumn(name = "document_id", nullable = false)
    private Document document;

    @Column(length = 4000)
    private String extractedText;

    private BigDecimal monthlyIncome;

    private BigDecimal monthlyExpenses;

    private BigDecimal existingDebtPayment;

    private BigDecimal averageMonthlyDeposits;

    private BigDecimal averageMonthlyWithdrawals;

    private BigDecimal businessRevenue;

    private BigDecimal taxValue;

    private BigDecimal invoiceTotal;

    private BigDecimal revenueConsistencyScore;

    private BigDecimal confidenceScore;

    @Column(nullable = false)
    private LocalDateTime createdAt;
}
