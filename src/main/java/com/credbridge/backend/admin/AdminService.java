package com.credbridge.backend.admin;

import com.credbridge.backend.application.ApplicationStatus;
import com.credbridge.backend.application.LoanApplication;
import com.credbridge.backend.application.LoanApplicationRepository;
import com.credbridge.backend.auth.CurrentUserService;
import com.credbridge.backend.auth.User;
import com.credbridge.backend.auth.UserRepository;
import com.credbridge.backend.auth.UserRole;
import com.credbridge.backend.scoring.CreditScore;
import com.credbridge.backend.scoring.CreditScoreRepository;
import com.credbridge.backend.scoring.RiskLevel;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

@Service
public class AdminService {

    private final LoanApplicationRepository loanApplicationRepository;
    private final CreditScoreRepository creditScoreRepository;
    private final UserRepository userRepository;
    private final CurrentUserService currentUserService;

    public AdminService(
            LoanApplicationRepository loanApplicationRepository,
            CreditScoreRepository creditScoreRepository,
            UserRepository userRepository,
            CurrentUserService currentUserService
    ) {
        this.loanApplicationRepository = loanApplicationRepository;
        this.creditScoreRepository = creditScoreRepository;
        this.userRepository = userRepository;
        this.currentUserService = currentUserService;
    }

    public List<AdminApplicationResponseDto> getApplications(String email) {
        requireAdmin(email);

        return loanApplicationRepository.findAll(Sort.by(Sort.Direction.DESC, "createdAt"))
                .stream()
                .map(this::toApplicationResponse)
                .toList();
    }

    public AdminOverviewResponseDto getOverview(String email) {
        requireAdmin(email);

        List<LoanApplication> applications = loanApplicationRepository.findAll();
        List<CreditScore> scores = creditScoreRepository.findAll();

        BigDecimal totalRequestedAmount = applications.stream()
                .map(LoanApplication::getRequestedAmount)
                .filter(amount -> amount != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);

        BigDecimal averageScore = scores.isEmpty()
                ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)
                : BigDecimal.valueOf(scores.stream()
                                .filter(score -> score.getScore() != null)
                                .mapToInt(CreditScore::getScore)
                                .average()
                                .orElse(0))
                        .setScale(2, RoundingMode.HALF_UP);

        return new AdminOverviewResponseDto(
                applications.size(),
                userRepository.count(),
                scores.size(),
                totalRequestedAmount,
                averageScore,
                applicationsByStatus(applications),
                scoresByRiskLevel(scores)
        );
    }

    private AdminApplicationResponseDto toApplicationResponse(LoanApplication application) {
        Optional<CreditScore> score = creditScoreRepository.findByApplicationId(application.getId());

        return new AdminApplicationResponseDto(
                application.getId(),
                application.getFullName(),
                application.getMode(),
                application.getStatus(),
                application.getRequestedAmount(),
                application.getTenureMonths(),
                application.getCreatedAt(),
                application.getUser() == null ? null : application.getUser().getId(),
                application.getUser() == null ? null : application.getUser().getEmail(),
                score.map(CreditScore::getScore).orElse(null),
                score.map(CreditScore::getRiskLevel).orElse(null),
                application.getReviewNotes(),
                application.getReviewedByUserId(),
                application.getReviewedAt()
        );
    }

    private Map<ApplicationStatus, Long> applicationsByStatus(List<LoanApplication> applications) {
        Map<ApplicationStatus, Long> values = new EnumMap<>(ApplicationStatus.class);
        for (ApplicationStatus status : ApplicationStatus.values()) {
            values.put(status, 0L);
        }
        for (LoanApplication application : applications) {
            values.computeIfPresent(application.getStatus(), (status, count) -> count + 1);
        }
        return values;
    }

    private Map<RiskLevel, Long> scoresByRiskLevel(List<CreditScore> scores) {
        Map<RiskLevel, Long> values = new EnumMap<>(RiskLevel.class);
        for (RiskLevel riskLevel : RiskLevel.values()) {
            values.put(riskLevel, 0L);
        }
        for (CreditScore score : scores) {
            values.computeIfPresent(score.getRiskLevel(), (riskLevel, count) -> count + 1);
        }
        return values;
    }

    private void requireAdmin(String email) {
        User user = currentUserService.requireUser(email);
        if (user.getRole() != UserRole.ADMIN) {
            throw new AccessDeniedException("Only admin users can access admin endpoints");
        }
    }
}
