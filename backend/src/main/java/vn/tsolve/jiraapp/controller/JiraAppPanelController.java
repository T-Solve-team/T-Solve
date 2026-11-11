package vn.tsolve.jiraapp.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import vn.tsolve.common.security.JiraAppPrincipal;
import vn.tsolve.common.security.Roles;
import vn.tsolve.jiraapp.dto.IssuePanelResponse;
import vn.tsolve.jiraapp.service.JiraAppSolutionService;

@RestController
@Tag(name = "jira-app")
public class JiraAppPanelController {

    private final JiraAppSolutionService solutions;

    public JiraAppPanelController(JiraAppSolutionService solutions) {
        this.solutions = solutions;
    }

    @GetMapping("/api/v1/jira-app/issues/{issueKey}/panel")
    @PreAuthorize("hasRole('" + Roles.JIRA_APP + "')")
    @Operation(operationId = "getIssuePanel", summary = "What the Issue Panel shows for the issue and the viewer")
    @ApiResponse(responseCode = "400", description = "Invalid issue key")
    public ResponseEntity<IssuePanelResponse> getIssuePanel(
            @AuthenticationPrincipal JiraAppPrincipal caller,
            @PathVariable @Size(max = 50) @Pattern(regexp = JiraAppSolutionController.ISSUE_KEY) String issueKey) {
        return ResponseEntity.ok(solutions.panel(caller, issueKey));
    }
}
