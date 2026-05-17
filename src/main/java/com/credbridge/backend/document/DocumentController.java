package com.credbridge.backend.document;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "Documents", description = "Upload and list borrower application documents")
public class DocumentController {

    private final DocumentService documentService;

    public DocumentController(DocumentService documentService) {
        this.documentService = documentService;
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Upload an application document")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Document uploaded"),
            @ApiResponse(responseCode = "400", description = "Invalid file, application ID, or document type"),
            @ApiResponse(responseCode = "401", description = "JWT token is missing or invalid"),
            @ApiResponse(responseCode = "403", description = "User cannot access the application"),
            @ApiResponse(responseCode = "404", description = "Application not found")
    })
    public DocumentResponseDto upload(
            @Parameter(description = "Application ID that owns the document")
            @RequestParam Long applicationId,
            @Parameter(description = "Document category")
            @RequestParam DocumentType documentType,
            @Parameter(description = "PDF, PNG, or JPEG file. Maximum size is configured by app.documents.max-file-size-bytes.")
            @RequestParam MultipartFile file,
            Principal principal
    ) {
        return toResponse(documentService.upload(applicationId, documentType, file, principal.getName()));
    }

    @GetMapping("/application/{applicationId}")
    @Operation(summary = "List documents for an application")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Documents returned"),
            @ApiResponse(responseCode = "401", description = "JWT token is missing or invalid"),
            @ApiResponse(responseCode = "403", description = "User cannot access the application"),
            @ApiResponse(responseCode = "404", description = "Application not found")
    })
    public List<DocumentResponseDto> getApplicationDocuments(
            @Parameter(description = "Application ID")
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
                document.getStatus(),
                document.getCreatedAt()
        );
    }
}
