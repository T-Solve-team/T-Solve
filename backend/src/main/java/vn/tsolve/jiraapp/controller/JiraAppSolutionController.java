package vn.tsolve.jiraapp.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.tsolve.common.security.JiraAppPrincipal;
import vn.tsolve.common.security.Roles;
import vn.tsolve.jiraapp.dto.SolutionFormRequest;
import vn.tsolve.jiraapp.dto.SolutionFormResponse;
import vn.tsolve.jiraapp.dto.SubmitSolutionRequest;
import vn.tsolve.jiraapp.dto.SubmittedTicketResponse;
import vn.tsolve.jiraapp.service.JiraAppSolutionService;
import vn.tsolve.ticket.dto.SolutionSections;

@RestController
@RequestMapping("/api/v1/jira-app")
@Tag(name = "jira-app")
public class JiraAppSolutionController {

    static final String ISSUE_KEY = "^[A-Z][A-Z0-9_]*-[1-9][0-9]*$";

    private final JiraAppSolutionService solutions;

    public JiraAppSolutionController(JiraAppSolutionService solutions) {
        this.solutions = solutions;
    }

    @PostMapping("/issues/{issueKey}/solution-form")
    @PreAuthorize("hasRole('" + Roles.JIRA_APP + "')")
    @Operation(operationId = "openSolutionForm", summary = "Whether the Solution Form may open, and its content")
    @ApiResponse(responseCode = "400", description = "Invalid issue key or body")
    public ResponseEntity<SolutionFormResponse> openSolutionForm(
            @AuthenticationPrincipal JiraAppPrincipal caller,
            @PathVariable @Size(max = 50) @Pattern(regexp = ISSUE_KEY) String issueKey,
            @Valid @RequestBody SolutionFormRequest request) {
        return ResponseEntity.ok(solutions.openForm(caller, issueKey, request));
    }

    @PostMapping("/issues/{issueKey}/solution")
    @PreAuthorize("hasRole('" + Roles.JIRA_APP + "')")
    @Operation(operationId = "submitSolution", summary = "Create the Solution Form ticket of the issue")
    @ApiResponse(responseCode = "400", description = "MSG01 or MSG02")
    @ApiResponse(responseCode = "403", description = "MSG62 or MSG08")
    @ApiResponse(responseCode = "409", description = "The issue already has a ticket")
    public ResponseEntity<SubmittedTicketResponse> submitSolution(
            @AuthenticationPrincipal JiraAppPrincipal caller,
            @PathVariable @Size(max = 50) @Pattern(regexp = ISSUE_KEY) String issueKey,
            @Valid @RequestBody SubmitSolutionRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(solutions.submit(caller, issueKey, request));
    }

    @PutMapping("/issues/{issueKey}/solution-draft")
    @PreAuthorize("hasRole('" + Roles.JIRA_APP + "')")
    @Operation(operationId = "saveSolutionDraft", summary = "Store the only Draft of the viewer for the issue")
    @ApiResponse(responseCode = "403", description = "MSG62 or MSG08")
    public ResponseEntity<Void> saveSolutionDraft(
            @AuthenticationPrincipal JiraAppPrincipal caller,
            @PathVariable @Size(max = 50) @Pattern(regexp = ISSUE_KEY) String issueKey,
            @Valid @RequestBody SolutionSections sections) {
        solutions.saveDraft(caller, issueKey, sections);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/solution-rewrites")
    @PreAuthorize("hasRole('" + Roles.JIRA_APP + "')")
    @Operation(operationId = "rewriteSolution", summary = "The AI rewrite of the five sections")
    @ApiResponse(responseCode = "403", description = "MSG62 or MSG08")
    @ApiResponse(responseCode = "503", description = "MSG68")
    public ResponseEntity<SolutionSections> rewriteSolution(
            @AuthenticationPrincipal JiraAppPrincipal caller, @Valid @RequestBody SolutionSections sections) {
        return ResponseEntity.ok(solutions.rewrite(caller, sections));
    }
}
