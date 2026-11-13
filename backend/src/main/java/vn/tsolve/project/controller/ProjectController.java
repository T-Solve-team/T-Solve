package vn.tsolve.project.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vn.tsolve.common.security.Roles;
import vn.tsolve.project.dto.AddProjectMembersRequest;
import vn.tsolve.project.dto.MemberCandidateResponse;
import vn.tsolve.project.dto.ProjectMemberResponse;
import vn.tsolve.project.dto.ProjectSummaryResponse;
import vn.tsolve.project.service.ProjectMemberService;

@RestController
@Tag(name = "projects")
@RequestMapping("/api/v1/projects/{projectId}")
public class ProjectController {
    private final ProjectMemberService service;

    public ProjectController(ProjectMemberService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(operationId = "getProject", summary = "Project Detail header")
    @ApiResponse(responseCode = "403", description = "Not the Project Manager of this Project")
    @ApiResponse(responseCode = "404", description = "Unknown Project")
    @PreAuthorize("hasRole('" + Roles.PROJECT_MANAGER + "')")
    public ResponseEntity<ProjectSummaryResponse> getProject(@PathVariable UUID projectId) {
        return ResponseEntity.ok(service.getProject(projectId));
    }

    @GetMapping("/members")
    @Operation(operationId = "listProjectMembers", summary = "Members of a Project")
    @ApiResponse(responseCode = "403", description = "Not the Project Manager of this Project")
    @ApiResponse(responseCode = "404", description = "Unknown Project")
    @PreAuthorize("hasRole('" + Roles.PROJECT_MANAGER + "')")
    public ResponseEntity<List<ProjectMemberResponse>> listProjectMembers(@PathVariable UUID projectId) {
        return ResponseEntity.ok(service.listMembers(projectId));
    }

    @GetMapping("/member-candidates")
    @Operation(
            operationId = "searchProjectMemberCandidates",
            summary = "Active Ticket Resolvers that can be added to a Project")
    @ApiResponse(responseCode = "400", description = "Search longer than 100 characters")
    @ApiResponse(responseCode = "403", description = "Not the Project Manager of this Project")
    @ApiResponse(responseCode = "404", description = "Unknown Project")
    @PreAuthorize("hasRole('" + Roles.PROJECT_MANAGER + "')")
    public ResponseEntity<List<MemberCandidateResponse>> searchProjectMemberCandidates(
            @PathVariable UUID projectId, @RequestParam(required = false) String q) {
        return ResponseEntity.ok(service.searchCandidates(projectId, q));
    }

    @PostMapping(path = "/members", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(operationId = "addProjectMembers", summary = "Add Ticket Resolvers to a Project")
    @ApiResponse(responseCode = "400", description = "Invalid userIds")
    @ApiResponse(responseCode = "403", description = "Not the Project Manager of this Project")
    @ApiResponse(responseCode = "404", description = "Unknown Project")
    @ApiResponse(responseCode = "409", description = "Project removed, or an id cannot be added")
    @PreAuthorize("hasRole('" + Roles.PROJECT_MANAGER + "')")
    public ResponseEntity<List<ProjectMemberResponse>> addProjectMembers(
            @PathVariable UUID projectId, @Valid @RequestBody AddProjectMembersRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.addMembers(projectId, request));
    }
}
