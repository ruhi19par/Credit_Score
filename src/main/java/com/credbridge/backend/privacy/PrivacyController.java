package com.credbridge.backend.privacy;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.security.Principal;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/privacy")
@Tag(name = "Privacy", description = "Consent history, consent revocation, and audit events")
public class PrivacyController {

    private final PrivacyService privacyService;

    public PrivacyController(PrivacyService privacyService) {
        this.privacyService = privacyService;
    }

    @GetMapping("/consents")
    @Operation(summary = "Show the authenticated user's consent history")
    public List<ConsentResponseDto> getConsentHistory(Principal principal) {
        return privacyService.getConsentHistory(principal.getName()).stream()
                .map(ConsentResponseDto::from)
                .toList();
    }

    @PatchMapping("/applications/{applicationId}/consent/revoke")
    @ResponseStatus(HttpStatus.OK)
    @Operation(summary = "Revoke consent for an application")
    public ConsentResponseDto revokeConsent(@PathVariable Long applicationId, Principal principal) {
        return ConsentResponseDto.from(privacyService.revokeConsent(applicationId, principal.getName()));
    }

    @GetMapping("/audit-events")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "List audit events for admin and compliance users")
    public List<AuditEventResponseDto> getAuditEvents(@RequestParam(required = false) Long applicationId) {
        return privacyService.getAuditEvents(applicationId).stream()
                .map(AuditEventResponseDto::from)
                .toList();
    }
}
