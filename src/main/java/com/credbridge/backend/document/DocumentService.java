package com.credbridge.backend.document;

import com.credbridge.backend.application.LoanApplication;
import com.credbridge.backend.application.LoanApplicationRepository;
import com.credbridge.backend.auth.CurrentUserService;
import com.credbridge.backend.auth.User;
import com.credbridge.backend.common.ResourceNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
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
    private final Path uploadRoot;

    public DocumentService(
            DocumentRepository documentRepository,
            LoanApplicationRepository loanApplicationRepository,
            CurrentUserService currentUserService,
            @Value("${app.documents.upload-dir:uploads/documents}") String uploadDir
    ) {
        this.documentRepository = documentRepository;
        this.loanApplicationRepository = loanApplicationRepository;
        this.currentUserService = currentUserService;
        this.uploadRoot = Path.of(uploadDir).normalize();
    }

    @Transactional
    public Document upload(Long applicationId, DocumentType documentType, MultipartFile file, String email) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Document file is required");
        }

        User user = currentUserService.requireUser(email);
        LoanApplication application = loanApplicationRepository.findById(applicationId)
                .orElseThrow(() -> new ResourceNotFoundException("Application not found: " + applicationId));
        requireAccess(application, user);

        String originalFilename = Path.of(StringUtils.cleanPath(
                file.getOriginalFilename() == null ? "document" : file.getOriginalFilename()
        )).getFileName().toString();
        String storedFilename = UUID.randomUUID() + "-" + originalFilename;
        Path applicationDirectory = uploadRoot.resolve(applicationId.toString()).normalize();
        Path storedPath = applicationDirectory.resolve(storedFilename).normalize();

        if (!storedPath.startsWith(applicationDirectory)) {
            throw new IllegalArgumentException("Invalid document filename");
        }

        try {
            Files.createDirectories(applicationDirectory);
            try (InputStream inputStream = file.getInputStream()) {
                Files.copy(inputStream, storedPath, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException exception) {
            throw new DocumentStorageException("Failed to store document", exception);
        }

        Document document = new Document();
        document.setApplication(application);
        document.setDocumentType(documentType);
        document.setOriginalFilename(originalFilename);
        document.setStoredFilePath(storedPath.toString());
        document.setStatus(DocumentStatus.UPLOADED);
        document.setCreatedAt(LocalDateTime.now());

        return documentRepository.save(document);
    }

    public List<Document> getApplicationDocuments(Long applicationId, String email) {
        User user = currentUserService.requireUser(email);
        LoanApplication application = loanApplicationRepository.findById(applicationId)
                .orElseThrow(() -> new ResourceNotFoundException("Application not found: " + applicationId));
        requireAccess(application, user);

        return documentRepository.findByApplicationIdOrderByCreatedAtDesc(applicationId);
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
