package com.credbridge.backend.application;

import com.credbridge.backend.scoring.ScoreResponseDto;
import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/applications")
public class ApplicationController {

    private final ApplicationService applicationService;

    public ApplicationController(ApplicationService applicationService) {
        this.applicationService = applicationService;
    }

    @PostMapping("/basic")
    @ResponseStatus(HttpStatus.CREATED)
    public ScoreResponseDto createBasicApplication(@Valid @RequestBody BasicApplicationRequestDto request) {
        return applicationService.createBasicApplication(request);
    }

    @GetMapping
    public List<ApplicationResponseDto> getApplications() {
        return applicationService.getApplications()
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @GetMapping("/{id}")
    public ApplicationResponseDto getApplication(@PathVariable Long id) {
        return toResponse(applicationService.getApplication(id));
    }

    @PatchMapping("/{id}/status")
    public ApplicationResponseDto updateStatus(
            @PathVariable Long id,
            @Valid @RequestBody UpdateApplicationStatusRequestDto request
    ) {
        return toResponse(applicationService.updateStatus(id, request.getStatus()));
    }

    private ApplicationResponseDto toResponse(LoanApplication application) {
        return new ApplicationResponseDto(
                application.getId(),
                application.getFullName(),
                application.getMode(),
                application.getStatus(),
                application.getRequestedAmount(),
                application.getTenureMonths(),
                application.getCreatedAt()
        );
    }

    public record ApplicationResponseDto(
            Long id,
            String fullName,
            ApplicationMode mode,
            ApplicationStatus status,
            BigDecimal requestedAmount,
            Integer tenureMonths,
            LocalDateTime createdAt
    ) {
    }
}
