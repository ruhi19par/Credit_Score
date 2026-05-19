package com.credbridge.backend.privacy;

import com.credbridge.backend.application.LoanApplication;
import com.credbridge.backend.application.LoanApplicationRepository;
import com.credbridge.backend.auth.User;
import com.credbridge.backend.auth.UserRepository;
import com.credbridge.backend.common.ResourceNotFoundException;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.access.AccessDeniedException;

@Service
public class PrivacyService {

    public static final String CREDIT_ASSESSMENT_PURPOSE = "CREDIT_ASSESSMENT";

    private final ConsentRecordRepository consentRecordRepository;
    private final AuditEventRepository auditEventRepository;
    private final UserRepository userRepository;
    private final LoanApplicationRepository loanApplicationRepository;

    public PrivacyService(
            ConsentRecordRepository consentRecordRepository,
            AuditEventRepository auditEventRepository,
            UserRepository userRepository,
            LoanApplicationRepository loanApplicationRepository
    ) {
        this.consentRecordRepository = consentRecordRepository;
        this.auditEventRepository = auditEventRepository;
        this.userRepository = userRepository;
        this.loanApplicationRepository = loanApplicationRepository;
    }

    public void recordConsent(LoanApplication application, User user, String purpose) {
        ConsentRecord consentRecord = new ConsentRecord();
        consentRecord.setApplication(application);
        consentRecord.setUser(user);
        consentRecord.setPurpose(purpose);
        consentRecord.setAccepted(true);
        consentRecord.setCreatedAt(LocalDateTime.now());
        consentRecordRepository.save(consentRecord);
    }

    public void requireActiveConsent(LoanApplication application, User user) {
        if (application == null || user == null) {
            throw new AccessDeniedException("Active consent is required");
        }

        boolean activeConsent = consentRecordRepository
                .findFirstByApplicationIdAndUserIdAndPurposeOrderByCreatedAtDesc(
                        application.getId(),
                        user.getId(),
                        CREDIT_ASSESSMENT_PURPOSE
                )
                .map(ConsentRecord::getAccepted)
                .orElse(false);

        if (!activeConsent) {
            throw new AccessDeniedException("Active consent is required for this application");
        }
    }

    public void requireActiveConsent(LoanApplication application) {
        boolean activeConsent = consentRecordRepository
                .findFirstByApplicationIdAndPurposeOrderByCreatedAtDesc(
                        application.getId(),
                        CREDIT_ASSESSMENT_PURPOSE
                )
                .map(ConsentRecord::getAccepted)
                .orElse(false);

        if (!activeConsent) {
            throw new AccessDeniedException("Active consent is required for document processing");
        }
    }

    public void audit(User user, LoanApplication application, String action, String details) {
        AuditEvent auditEvent = new AuditEvent();
        auditEvent.setUser(user);
        auditEvent.setApplication(application);
        auditEvent.setAction(action);
        auditEvent.setDetails(details);
        auditEvent.setCreatedAt(LocalDateTime.now());
        auditEventRepository.save(auditEvent);
    }

    public List<ConsentRecord> getConsentHistory(String email) {
        return consentRecordRepository.findByUserEmailIgnoreCaseOrderByCreatedAtDesc(email);
    }

    @Transactional
    public ConsentRecord revokeConsent(Long applicationId, String email) {
        User user = userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        LoanApplication application = loanApplicationRepository.findById(applicationId)
                .orElseThrow(() -> new ResourceNotFoundException("Application not found: " + applicationId));
        if (application.getUser() == null || !application.getUser().getId().equals(user.getId())) {
            throw new AccessDeniedException("You do not have access to this application");
        }

        ConsentRecord consentRecord = new ConsentRecord();
        consentRecord.setApplication(application);
        consentRecord.setUser(user);
        consentRecord.setPurpose(CREDIT_ASSESSMENT_PURPOSE);
        consentRecord.setAccepted(false);
        consentRecord.setCreatedAt(LocalDateTime.now());
        ConsentRecord savedConsent = consentRecordRepository.save(consentRecord);
        audit(user, application, "CONSENT_REVOKED", "Consent revoked for " + CREDIT_ASSESSMENT_PURPOSE);
        return savedConsent;
    }

    public List<AuditEvent> getAuditEvents(Long applicationId) {
        if (applicationId == null) {
            return auditEventRepository.findTop200ByOrderByCreatedAtDesc();
        }
        return auditEventRepository.findByApplicationIdOrderByCreatedAtDesc(applicationId);
    }
}
