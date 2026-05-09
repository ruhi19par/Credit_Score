package com.credbridge.backend.report;

import com.credbridge.backend.application.LoanApplication;
import com.credbridge.backend.application.LoanApplicationRepository;
import com.credbridge.backend.common.ResourceNotFoundException;
import com.credbridge.backend.financial.FinancialProfile;
import com.credbridge.backend.financial.FinancialProfileRepository;
import com.credbridge.backend.scoring.CreditScore;
import com.credbridge.backend.scoring.CreditScoreRepository;
import java.util.Arrays;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class ReportService {

    private final LoanApplicationRepository loanApplicationRepository;
    private final FinancialProfileRepository financialProfileRepository;
    private final CreditScoreRepository creditScoreRepository;

    public ReportService(
            LoanApplicationRepository loanApplicationRepository,
            FinancialProfileRepository financialProfileRepository,
            CreditScoreRepository creditScoreRepository
    ) {
        this.loanApplicationRepository = loanApplicationRepository;
        this.financialProfileRepository = financialProfileRepository;
        this.creditScoreRepository = creditScoreRepository;
    }

    public ReportResponseDto getReport(Long applicationId) {
        LoanApplication application = loanApplicationRepository.findById(applicationId)
                .orElseThrow(() -> new ResourceNotFoundException("Application not found: " + applicationId));

        FinancialProfile financialProfile = financialProfileRepository.findByApplicationId(applicationId)
                .orElseThrow(() -> new ResourceNotFoundException("Financial profile not found: " + applicationId));

        CreditScore creditScore = creditScoreRepository.findByApplicationId(applicationId)
                .orElseThrow(() -> new ResourceNotFoundException("Credit score not found: " + applicationId));

        return new ReportResponseDto(
                application.getId(),
                application.getFullName(),
                application.getMode(),
                application.getStatus(),
                application.getRequestedAmount(),
                application.getTenureMonths(),
                application.getCreatedAt(),
                financialProfile.getEmploymentType(),
                financialProfile.getMonthlyIncome(),
                financialProfile.getMonthlyExpenses(),
                financialProfile.getExistingDebtPayment(),
                financialProfile.getRepaymentHistory(),
                financialProfile.getIncomeStability(),
                creditScore.getScore(),
                creditScore.getRiskLevel(),
                creditScore.getDtiRatio(),
                creditScore.getExpenseRatio(),
                creditScore.getRepaymentCapacity(),
                creditScore.getSuggestedLoanLimit(),
                toList(creditScore.getPositiveFactors()),
                toList(creditScore.getRiskFactors())
        );
    }

    private List<String> toList(String storedText) {
        if (storedText == null || storedText.isBlank()) {
            return List.of();
        }

        return Arrays.stream(storedText.split("\\n"))
                .filter(value -> !value.isBlank())
                .toList();
    }
}
