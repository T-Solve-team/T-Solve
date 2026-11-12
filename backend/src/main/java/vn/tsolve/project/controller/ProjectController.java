package vn.tsolve.project.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.tsolve.common.security.Roles;
import vn.tsolve.project.dto.CreateProjectRequest;
import vn.tsolve.project.dto.ProjectConnectionTestRequest;
import vn.tsolve.project.dto.ProjectConnectionTestResponse;
import vn.tsolve.project.dto.ProjectResponse;
import vn.tsolve.project.dto.ProjectSaveResult;
import vn.tsolve.project.service.ProjectService;

@RestController
@Tag(name = "projects")
@RequestMapping("/api/v1/projects")
public class ProjectController {
    private final ProjectService service;

    public ProjectController(ProjectService service) {
        this.service = service;
    }

    @PostMapping(path = "/connection-test", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            operationId = "testProjectConnection",
            summary = "Test a Jira project connection (Project Manager only).")
    @ApiResponse(responseCode = "400", description = "Invalid request")
    @ApiResponse(responseCode = "403", description = "Access denied")
    @ApiResponse(responseCode = "409", description = "Project unavailable or already linked")
    @ApiResponse(responseCode = "503", description = "Tracker unavailable")
    @PreAuthorize("hasRole('" + Roles.PROJECT_MANAGER + "')")
    public ResponseEntity<ProjectConnectionTestResponse> testConnection(
            @Valid @RequestBody ProjectConnectionTestRequest request) {
        return ResponseEntity.ok(service.testConnection(request));
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(operationId = "createProject", summary = "Add or reconnect a Project (Project Manager only).")
    @ApiResponse(responseCode = "400", description = "Invalid request")
    @ApiResponse(responseCode = "403", description = "Access denied")
    @ApiResponse(responseCode = "409", description = "Project unavailable or already linked")
    @ApiResponse(responseCode = "503", description = "Tracker unavailable")
    @PreAuthorize("hasRole('" + Roles.PROJECT_MANAGER + "')")
    public ResponseEntity<ProjectResponse> create(@Valid @RequestBody CreateProjectRequest request) {
        ProjectSaveResult saved = service.create(request);
        return ResponseEntity.status(saved.created() ? HttpStatus.CREATED : HttpStatus.OK)
                .body(saved.project());
    }
}
