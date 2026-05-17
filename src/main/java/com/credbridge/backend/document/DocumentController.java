package com.credbridge.backend.document;

import java.security.Principal;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/documents")
public class DocumentController {

    private final DocumentService documentService;

    public DocumentController(DocumentService documentService) {
        this.documentService = documentService;
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public DocumentResponseDto upload(
            @RequestParam Long applicationId,
            @RequestParam DocumentType documentType,
            @RequestParam MultipartFile file,
            Principal principal
    ) {
        return toResponse(documentService.upload(applicationId, documentType, file, principal.getName()));
    }

    @GetMapping("/application/{applicationId}")
    public List<DocumentResponseDto> getApplicationDocuments(
            @PathVariable Long applicationId,
            Principal principal
    ) {
        return documentService.getApplicationDocuments(applicationId, principal.getName())
                .stream()
                .map(this::toResponse)
                .toList();
    }

    private DocumentResponseDto toResponse(Document document) {
        return new DocumentResponseDto(
                document.getId(),
                document.getApplication().getId(),
                document.getDocumentType(),
                document.getOriginalFilename(),
                document.getStoredFilePath(),
                document.getStatus(),
                document.getCreatedAt()
        );
    }
}
