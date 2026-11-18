package vn.tsolve.jiraapp.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.tsolve.common.security.JiraAppPrincipal;
import vn.tsolve.common.security.Roles;
import vn.tsolve.jiraapp.dto.IssueUpdatedPayload;
import vn.tsolve.jiraapp.service.IssueUpdatedService;

@Tag(name = "jira-app")
@RestController
@RequestMapping("/api/v1/jira-app/events")
public class JiraAppIssueEventController {

    private final IssueUpdatedService issueUpdatedService;

    public JiraAppIssueEventController(IssueUpdatedService issueUpdatedService) {
        this.issueUpdatedService = issueUpdatedService;
    }

    @Operation(operationId = "receiveIssueUpdated", summary = "Forge trigger avi:jira:updated:issue")
    @PreAuthorize("hasRole('" + Roles.JIRA_APP + "')")
    @PostMapping("/issue-updated")
    public ResponseEntity<Void> receiveIssueUpdated(
            @AuthenticationPrincipal JiraAppPrincipal caller, @RequestBody IssueUpdatedPayload event) {
        issueUpdatedService.receive(caller, event);
        return ResponseEntity.accepted().build();
    }
}
