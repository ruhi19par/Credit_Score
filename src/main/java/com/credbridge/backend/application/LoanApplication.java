package com.credbridge.backend.application;

import com.credbridge.backend.financial.FinancialProfile;
import com.credbridge.backend.scoring.CreditScore;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "loan_applications")
public class LoanApplication {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String fullName;

    @Enumerated(EnumType.STRING)
    private ApplicationMode mode;

    @Enumerated(EnumType.STRING)
    private ApplicationStatus status;

    private BigDecimal requestedAmount;

    private Integer tenureMonths;

    private LocalDateTime createdAt;

    @OneToOne(mappedBy = "application", cascade = CascadeType.ALL)
    private FinancialProfile financialProfile;

    @OneToOne(mappedBy = "application", cascade = CascadeType.ALL)
    private CreditScore creditScore;
}
