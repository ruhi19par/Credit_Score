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
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

@Service
public class DocumentService {

    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "application/pdf",
            "image/png",
            "image/jpeg"
    );

    private final DocumentRepository documentRepository;
    private final LoanApplicationRepository loanApplicationRepository;
    private final CurrentUserService currentUserService;
    private final DocumentProcessingService documentProcessingService;
    private final Path uploadRoot;
    private final long maxFileSizeBytes;

    public DocumentService(
            DocumentRepository documentRepository,
            LoanApplicationRepository loanApplicationRepository,
            CurrentUserService currentUserService,
            DocumentProcessingService documentProcessingService,
            @Value("${app.documents.upload-dir:uploads/documents}") String uploadDir,
            @Value("${app.documents.max-file-size-bytes:5242880}") long maxFileSizeBytes
    ) {
        this.documentRepository = documentRepository;
        this.loanApplicationRepository = loanApplicationRepository;
        this.currentUserService = currentUserService;
        this.documentProcessingService = documentProcessingService;
        this.uploadRoot = Path.of(uploadDir).normalize();
        this.maxFileSizeBytes = maxFileSizeBytes;
    }

    @Transactional
    public Document upload(Long applicationId, DocumentType documentType, MultipartFile file, String email) {
        validateFile(file);

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

        Document savedDocument = documentRepository.save(document);
        documentProcessingService.process(savedDocument);
        return savedDocument;
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Document file is required");
        }
        if (file.getSize() > maxFileSizeBytes) {
            throw new IllegalArgumentException("Document file exceeds maximum allowed size");
        }
        if (!ALLOWED_CONTENT_TYPES.contains(file.getContentType())) {
            throw new IllegalArgumentException("Document file type is not supported");
        }
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
