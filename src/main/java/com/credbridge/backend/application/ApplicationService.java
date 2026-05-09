package com.credbridge.backend.application;

import com.credbridge.backend.common.ResourceNotFoundException;
import com.credbridge.backend.financial.FinancialProfile;
import com.credbridge.backend.scoring.BasicScoringService;
import com.credbridge.backend.scoring.CreditScore;
import com.credbridge.backend.scoring.CreditScoreRepository;
import com.credbridge.backend.scoring.ScoreResponseDto;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ApplicationService {

    private final LoanApplicationRepository loanApplicationRepository;
    private final CreditScoreRepository creditScoreRepository;
    private final BasicScoringService basicScoringService;

    public ApplicationService(
            LoanApplicationRepository loanApplicationRepository,
            CreditScoreRepository creditScoreRepository,
            BasicScoringService basicScoringService
    ) {
        this.loanApplicationRepository = loanApplicationRepository;
        this.creditScoreRepository = creditScoreRepository;
        this.basicScoringService = basicScoringService;
    }

    @Transactional
    public ScoreResponseDto createBasicApplication(BasicApplicationRequestDto request) {
        LoanApplication application = new LoanApplication();
        application.setFullName(request.getFullName());
        application.setMode(ApplicationMode.BASIC);
        application.setStatus(ApplicationStatus.SUBMITTED);
        application.setRequestedAmount(request.getRequestedAmount());
        application.setTenureMonths(request.getTenureMonths());
        application.setCreatedAt(LocalDateTime.now());

        FinancialProfile financialProfile = new FinancialProfile();
        financialProfile.setApplication(application);
        financialProfile.setEmploymentType(request.getEmploymentType());
        financialProfile.setMonthlyIncome(request.getMonthlyIncome());
        financialProfile.setMonthlyExpenses(request.getMonthlyExpenses());
        financialProfile.setExistingDebtPayment(request.getExistingDebtPayment());
        financialProfile.setRepaymentHistory(request.getRepaymentHistory());
        financialProfile.setIncomeStability(request.getIncomeStability());
        application.setFinancialProfile(financialProfile);

        LoanApplication savedApplication = loanApplicationRepository.save(application);
        ScoreResponseDto scoreResponse = basicScoringService.calculate(request);

        CreditScore creditScore = new CreditScore();
        creditScore.setApplication(savedApplication);
        creditScore.setScore(scoreResponse.getScore());
        creditScore.setRiskLevel(scoreResponse.getRiskLevel());
        creditScore.setDtiRatio(scoreResponse.getDebtToIncomeRatio());
        creditScore.setExpenseRatio(scoreResponse.getExpenseRatio());
        creditScore.setRepaymentCapacity(scoreResponse.getRepaymentCapacity());
        creditScore.setSuggestedLoanLimit(scoreResponse.getSuggestedLoanLimit());
        creditScore.setPositiveFactors(toStoredText(scoreResponse.getPositiveFactors()));
        creditScore.setRiskFactors(toStoredText(scoreResponse.getRiskFactors()));
        creditScore.setCreatedAt(LocalDateTime.now());
        creditScoreRepository.save(creditScore);

        savedApplication.setStatus(ApplicationStatus.SCORED);

        return new ScoreResponseDto(
                savedApplication.getId(),
                scoreResponse.getScore(),
                scoreResponse.getRiskLevel(),
                scoreResponse.getDebtToIncomeRatio(),
                scoreResponse.getExpenseRatio(),
                scoreResponse.getRepaymentCapacity(),
                scoreResponse.getSuggestedLoanLimit(),
                scoreResponse.getPositiveFactors(),
                scoreResponse.getRiskFactors()
        );
    }

    public List<LoanApplication> getApplications() {
        return loanApplicationRepository.findAll();
    }

    public LoanApplication getApplication(Long id) {
        return loanApplicationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Application not found: " + id));
    }

    @Transactional
    public LoanApplication updateStatus(Long id, ApplicationStatus status) {
        LoanApplication application = getApplication(id);
        application.setStatus(status);
        return application;
    }

    private String toStoredText(List<String> values) {
        return String.join("\n", values);
    }
}
