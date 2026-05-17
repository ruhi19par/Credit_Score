package com.credbridge.backend.admin;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.security.Principal;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin")
@Tag(name = "Admin", description = "Admin dashboard and portfolio overview APIs")
public class AdminController {

    private final AdminService adminService;

    public AdminController(AdminService adminService) {
        this.adminService = adminService;
    }

    @GetMapping("/applications")
    @Operation(summary = "List all applications for admin review")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Applications returned"),
            @ApiResponse(responseCode = "401", description = "JWT token is missing or invalid"),
            @ApiResponse(responseCode = "403", description = "Authenticated user is not an admin")
    })
    public List<AdminApplicationResponseDto> getApplications(Principal principal) {
        return adminService.getApplications(principal.getName());
    }

    @GetMapping("/overview")
    @Operation(summary = "Get admin dashboard overview metrics")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Overview returned"),
            @ApiResponse(responseCode = "401", description = "JWT token is missing or invalid"),
            @ApiResponse(responseCode = "403", description = "Authenticated user is not an admin")
    })
    public AdminOverviewResponseDto getOverview(Principal principal) {
        return adminService.getOverview(principal.getName());
    }
}
