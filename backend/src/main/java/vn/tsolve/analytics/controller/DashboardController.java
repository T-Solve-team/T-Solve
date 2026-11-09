package vn.tsolve.analytics.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vn.tsolve.analytics.dto.KnowledgeDashboardResponse;
import vn.tsolve.analytics.service.DashboardService;
import vn.tsolve.common.security.Roles;
import vn.tsolve.common.security.TSolveUser;

@RestController
@Tag(name = "dashboard")
public class DashboardController {
    private final DashboardService service;

    public DashboardController(DashboardService service) {
        this.service = service;
    }

    @GetMapping("/api/v1/dashboard")
    @Operation(operationId = "getKnowledgeDashboard")
    @ApiResponse(responseCode = "400", description = "Invalid filters")
    @ApiResponse(responseCode = "403", description = "Access denied")
    @ApiResponse(responseCode = "404", description = "Unknown Department or Project")
    @PreAuthorize("hasAnyRole('" + Roles.PROJECT_MANAGER + "', '" + Roles.ADMIN + "')")
    public ResponseEntity<KnowledgeDashboardResponse> dashboard(
            @AuthenticationPrincipal TSolveUser user,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(required = false) String departmentId,
            @RequestParam(required = false) String projectId) {
        return ResponseEntity.ok(service.dashboard(user, from, to, departmentId, projectId));
    }
}
