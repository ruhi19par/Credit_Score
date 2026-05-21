package com.credbridge.backend.document;

import com.credbridge.backend.application.LoanApplication;
import com.credbridge.backend.application.LoanApplicationRepository;
import com.credbridge.backend.auth.CurrentUserService;
import com.credbridge.backend.auth.User;
import com.credbridge.backend.common.ResourceNotFoundException;
import com.credbridge.backend.privacy.PrivacyService;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

@Service
public class DocumentService {

    private final DocumentRepository documentRepository;
    private final LoanApplicationRepository loanApplicationRepository;
    private final CurrentUserService currentUserService;
    private final DocumentProcessingService documentProcessingService;
    private final PrivacyService privacyService;
    private final FileSecurityService fileSecurityService;
    private final DocumentStorageService documentStorageService;
    private final ExtractedFinancialFieldsRepository extractedFinancialFieldsRepository;
    private final boolean asyncProcessingEnabled;

    public DocumentService(
            DocumentRepository documentRepository,
            LoanApplicationRepository loanApplicationRepository,
            CurrentUserService currentUserService,
            DocumentProcessingService documentProcessingService,
            PrivacyService privacyService,
            FileSecurityService fileSecurityService,
            DocumentStorageService documentStorageService,
            ExtractedFinancialFieldsRepository extractedFinancialFieldsRepository,
            @Value("${app.documents.async-processing:false}") boolean asyncProcessingEnabled
    ) {
        this.documentRepository = documentRepository;
        this.loanApplicationRepository = loanApplicationRepository;
        this.currentUserService = currentUserService;
        this.documentProcessingService = documentProcessingService;
        this.privacyService = privacyService;
        this.fileSecurityService = fileSecurityService;
        this.documentStorageService = documentStorageService;
        this.extractedFinancialFieldsRepository = extractedFinancialFieldsRepository;
        this.asyncProcessingEnabled = asyncProcessingEnabled;
    }

    @Transactional
    public Document upload(Long applicationId, DocumentType documentType, MultipartFile file, String email) {
        ValidatedDocumentFile validatedFile = fileSecurityService.validate(file);

        User user = currentUserService.requireUser(email);
        LoanApplication application = loanApplicationRepository.findById(applicationId)
                .orElseThrow(() -> new ResourceNotFoundException("Application not found: " + applicationId));
        requireAccess(application, user);
        privacyService.requireActiveConsent(application, user);

        String originalFilename = Path.of(StringUtils.cleanPath(
                file.getOriginalFilename() == null ? "document" : file.getOriginalFilename()
        )).getFileName().toString();
        String storedFilename = UUID.randomUUID() + "-" + originalFilename;
        String storageKey = applicationId + "/" + storedFilename;

        StoredDocument storedDocument;
        try {
            try (InputStream inputStream = Files.newInputStream(validatedFile.path())) {
                storedDocument = documentStorageService.store(
                        storageKey,
                        inputStream,
                        validatedFile.size(),
                        validatedFile.contentType()
                );
            }
        } catch (IOException exception) {
            throw new DocumentStorageException("Failed to store document", exception);
        } finally {
            try {
                Files.deleteIfExists(validatedFile.path());
            } catch (IOException ignored) {
                // Temporary upload validation files are best-effort cleanup.
            }
        }

        Document document = new Document();
        document.setApplication(application);
        document.setDocumentType(documentType);
        document.setOriginalFilename(originalFilename);
        document.setStoredFilePath(storedDocument.location());
        document.setStatus(DocumentStatus.UPLOADED);
        document.setCreatedAt(LocalDateTime.now());

        Document savedDocument = documentRepository.save(document);
        privacyService.audit(
                user,
                application,
                "DOCUMENT_UPLOADED",
                documentType + " document uploaded as " + originalFilename
        );
        if (asyncProcessingEnabled) {
            documentProcessingService.processAsync(savedDocument.getId());
        } else {
            documentProcessingService.process(savedDocument.getId());
        }
        return savedDocument;
    }

    public List<Document> getApplicationDocuments(Long applicationId, String email) {
        User user = currentUserService.requireUser(email);
        LoanApplication application = loanApplicationRepository.findById(applicationId)
                .orElseThrow(() -> new ResourceNotFoundException("Application not found: " + applicationId));
        requireAccess(application, user);
        if (!currentUserService.isStaff(user)) {
            privacyService.requireActiveConsent(application, user);
        }

        return documentRepository.findByApplicationIdOrderByCreatedAtDesc(applicationId);
    }

    @Transactional
    public void deleteDocument(Long documentId, String email) {
        User user = currentUserService.requireUser(email);
        Document document = documentRepository.findById(documentId)
                .orElseThrow(() -> new ResourceNotFoundException("Document not found: " + documentId));
        requireAccess(document.getApplication(), user);
        if (!currentUserService.isStaff(user)) {
            privacyService.requireActiveConsent(document.getApplication(), user);
        }
        documentStorageService.delete(document);
        extractedFinancialFieldsRepository.deleteByDocumentId(documentId);
        documentRepository.delete(document);
        privacyService.audit(
                user,
                document.getApplication(),
                "DOCUMENT_DELETED",
                document.getDocumentType() + " document deleted"
        );
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
}
