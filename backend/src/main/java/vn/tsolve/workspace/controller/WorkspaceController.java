package vn.tsolve.workspace.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.tsolve.common.security.Roles;
import vn.tsolve.workspace.dto.WorkspaceInfoResponse;
import vn.tsolve.workspace.dto.WorkspaceSetupRequest;
import vn.tsolve.workspace.dto.WorkspaceUpdateRequest;
import vn.tsolve.workspace.service.WorkspaceService;

@RestController
@Tag(name = "workspace")
@RequestMapping("/api/v1/workspace")
public class WorkspaceController {
    private final WorkspaceService service;
    private final ObjectMapper mapper;

    public WorkspaceController(WorkspaceService service, ObjectMapper mapper) {
        this.service = service;
        this.mapper = mapper;
    }

    @PostMapping(value = "/setup", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(operationId = "setupWorkspace", summary = "Set up the Workspace once")
    @ApiResponse(
            responseCode = "201",
            description = "Workspace set up",
            content = @Content(schema = @Schema(implementation = WorkspaceInfoResponse.class)))
    @ApiResponse(responseCode = "400", description = "Invalid Workspace fields or Jira account")
    @ApiResponse(responseCode = "403", description = "Admin role required")
    @ApiResponse(responseCode = "409", description = "WORKSPACE_ALREADY_SET_UP")
    @PreAuthorize("hasRole('" + Roles.ADMIN + "')")
    public ResponseEntity<WorkspaceInfoResponse> setup(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                            required = true,
                            content = @Content(schema = @Schema(implementation = WorkspaceSetupRequest.class)))
                    @RequestBody(required = false)
                    String request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(service.setup(WorkspaceSetupRequest.parse(request, mapper).body()));
    }

    @GetMapping
    @Operation(operationId = "getWorkspace", summary = "Read the Workspace information")
    @ApiResponse(responseCode = "403", description = "Admin role required")
    @ApiResponse(responseCode = "404", description = "Workspace not set up (MSG60)")
    @PreAuthorize("hasRole('" + Roles.ADMIN + "')")
    public ResponseEntity<WorkspaceInfoResponse> get() {
        return ResponseEntity.ok(service.get());
    }

    @PutMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(operationId = "updateWorkspace", summary = "Update company information and service account")
    @ApiResponse(responseCode = "400", description = "Invalid Workspace fields or Jira account")
    @ApiResponse(responseCode = "403", description = "Admin role required")
    @ApiResponse(responseCode = "404", description = "Workspace not set up (MSG60)")
    @PreAuthorize("hasRole('" + Roles.ADMIN + "')")
    public ResponseEntity<WorkspaceInfoResponse> update(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                            required = true,
                            content = @Content(schema = @Schema(implementation = WorkspaceUpdateRequest.class)))
                    @RequestBody(required = false)
                    String request) {
        return ResponseEntity.ok(
                service.update(WorkspaceUpdateRequest.parse(request, mapper).body()));
    }
}
