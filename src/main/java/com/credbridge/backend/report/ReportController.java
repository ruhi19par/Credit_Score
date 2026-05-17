package com.credbridge.backend.report;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.security.Principal;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/reports")
@Tag(name = "Reports", description = "Credit report retrieval and export APIs")
public class ReportController {

    private final ReportService reportService;
    private final ReportPdfService reportPdfService;

    public ReportController(ReportService reportService, ReportPdfService reportPdfService) {
        this.reportService = reportService;
        this.reportPdfService = reportPdfService;
    }

    @GetMapping("/{applicationId}")
    @Operation(summary = "Get credit report data")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Report returned"),
            @ApiResponse(responseCode = "401", description = "JWT token is missing or invalid"),
            @ApiResponse(responseCode = "403", description = "User cannot access the report"),
            @ApiResponse(responseCode = "404", description = "Report data not found")
    })
    public ReportResponseDto getReport(@PathVariable Long applicationId, Principal principal) {
        return reportService.getReport(applicationId, principal.getName());
    }

    @GetMapping(value = "/{applicationId}/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    @Operation(summary = "Download credit report as PDF")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "PDF report returned"),
            @ApiResponse(responseCode = "401", description = "JWT token is missing or invalid"),
            @ApiResponse(responseCode = "403", description = "User cannot access the report"),
            @ApiResponse(responseCode = "404", description = "Report data not found")
    })
    public ResponseEntity<byte[]> getReportPdf(@PathVariable Long applicationId, Principal principal) {
        ReportResponseDto report = reportService.getReport(applicationId, principal.getName());
        byte[] pdf = reportPdfService.generate(report);

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .contentLength(pdf.length)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename("credit-report-" + applicationId + ".pdf")
                        .build()
                        .toString())
                .body(pdf);
    }
}
