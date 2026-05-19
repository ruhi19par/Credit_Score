package com.credbridge.backend.application;

import com.credbridge.backend.auth.CurrentUserService;
import com.credbridge.backend.auth.User;
import com.credbridge.backend.common.ResourceNotFoundException;
import com.credbridge.backend.financial.FinancialProfile;
import com.credbridge.backend.privacy.PrivacyService;
import com.credbridge.backend.scoring.BasicScoringService;
import com.credbridge.backend.scoring.CreditScore;
import com.credbridge.backend.scoring.CreditScoreRepository;
import com.credbridge.backend.scoring.ScoreResponseDto;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ApplicationService {

    private final LoanApplicationRepository loanApplicationRepository;
    private final CreditScoreRepository creditScoreRepository;
    private final BasicScoringService basicScoringService;
    private final CurrentUserService currentUserService;
    private final PrivacyService privacyService;

    public ApplicationService(
            LoanApplicationRepository loanApplicationRepository,
            CreditScoreRepository creditScoreRepository,
            BasicScoringService basicScoringService,
            CurrentUserService currentUserService,
            PrivacyService privacyService
    ) {
        this.loanApplicationRepository = loanApplicationRepository;
        this.creditScoreRepository = creditScoreRepository;
        this.basicScoringService = basicScoringService;
        this.currentUserService = currentUserService;
        this.privacyService = privacyService;
    }

    @Transactional
    public ScoreResponseDto createBasicApplication(BasicApplicationRequestDto request, String email) {
        LoanApplication savedApplication = createApplication(request, email, ApplicationMode.BASIC);
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

    @Transactional
    public LoanApplication createVerifiedApplication(BasicApplicationRequestDto request, String email) {
        return createApplication(request, email, ApplicationMode.VERIFIED);
    }

    public List<LoanApplication> getApplications(String email) {
        User user = currentUserService.requireUser(email);
        if (currentUserService.isStaff(user)) {
            return loanApplicationRepository.findAll();
        }

        return loanApplicationRepository.findByUserEmailIgnoreCase(email);
    }

    public LoanApplication getApplication(Long id, String email) {
        User user = currentUserService.requireUser(email);
        LoanApplication application = loanApplicationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Application not found: " + id));

        requireAccess(application, user);
        return application;
    }

    @Transactional
    public LoanApplication updateStatus(Long id, ApplicationStatus status, String reviewNotes, String email) {
        User user = currentUserService.requireUser(email);
        if (!currentUserService.isStaff(user)) {
            throw new AccessDeniedException("Only staff users can update application status");
        }

        LoanApplication application = loanApplicationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Application not found: " + id));
        application.setStatus(status);
        application.setReviewNotes(reviewNotes == null || reviewNotes.isBlank() ? null : reviewNotes.trim());
        application.setReviewedByUserId(user.getId());
        application.setReviewedAt(LocalDateTime.now());
        privacyService.audit(
                user,
                application,
                "APPLICATION_STATUS_UPDATED",
                "Updated status to " + status + (application.getReviewNotes() == null ? "" : ": " + application.getReviewNotes())
        );
        return application;
    }

    private void requireAccess(LoanApplication application, User user) {
        if (currentUserService.isStaff(user)) {
            return;
        }

        if (application.getUser() != null && application.getUser().getId().equals(user.getId())) {
            return;
        }

        throw new AccessDeniedException("You do not have access to this application");
    }

    private String toStoredText(List<String> values) {
        return String.join("\n", values);
    }

    private LoanApplication createApplication(BasicApplicationRequestDto request, String email, ApplicationMode mode) {
        User user = currentUserService.requireUser(email);

        LoanApplication application = new LoanApplication();
        application.setFullName(request.getFullName());
        application.setMode(mode);
        application.setStatus(ApplicationStatus.SUBMITTED);
        application.setRequestedAmount(request.getRequestedAmount());
        application.setTenureMonths(request.getTenureMonths());
        application.setCreatedAt(LocalDateTime.now());
        application.setUser(user);

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
        privacyService.recordConsent(savedApplication, user, PrivacyService.CREDIT_ASSESSMENT_PURPOSE);
        privacyService.audit(
                user,
                savedApplication,
                "APPLICATION_CREATED",
                "Created " + mode + " application"
        );
        return savedApplication;
    }
}
