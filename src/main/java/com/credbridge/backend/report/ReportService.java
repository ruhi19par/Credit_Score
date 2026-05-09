package com.credbridge.backend.report;

import com.credbridge.backend.auth.CurrentUserService;
import com.credbridge.backend.auth.User;
import com.credbridge.backend.application.LoanApplication;
import com.credbridge.backend.application.LoanApplicationRepository;
import com.credbridge.backend.common.ResourceNotFoundException;
import com.credbridge.backend.financial.FinancialProfile;
import com.credbridge.backend.financial.FinancialProfileRepository;
import com.credbridge.backend.scoring.CreditScore;
import com.credbridge.backend.scoring.CreditScoreRepository;
import java.util.Arrays;
import java.util.List;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

@Service
public class ReportService {

    private final LoanApplicationRepository loanApplicationRepository;
    private final FinancialProfileRepository financialProfileRepository;
    private final CreditScoreRepository creditScoreRepository;
    private final CurrentUserService currentUserService;

    public ReportService(
            LoanApplicationRepository loanApplicationRepository,
            FinancialProfileRepository financialProfileRepository,
            CreditScoreRepository creditScoreRepository,
            CurrentUserService currentUserService
    ) {
        this.loanApplicationRepository = loanApplicationRepository;
        this.financialProfileRepository = financialProfileRepository;
        this.creditScoreRepository = creditScoreRepository;
        this.currentUserService = currentUserService;
    }

    public ReportResponseDto getReport(Long applicationId, String email) {
        User user = currentUserService.requireUser(email);
        LoanApplication application = loanApplicationRepository.findById(applicationId)
                .orElseThrow(() -> new ResourceNotFoundException("Application not found: " + applicationId));
        requireAccess(application, user);

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

    private void requireAccess(LoanApplication application, User user) {
        if (currentUserService.isStaff(user)) {
            return;
        }

        if (application.getUser() != null && application.getUser().getId().equals(user.getId())) {
            return;
        }

        throw new AccessDeniedException("You do not have access to this report");
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
