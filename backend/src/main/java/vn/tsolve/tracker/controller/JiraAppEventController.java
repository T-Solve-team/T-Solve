package vn.tsolve.tracker.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.tsolve.common.security.JiraAppPrincipal;
import vn.tsolve.common.security.Roles;
import vn.tsolve.common.security.WorkspaceContext;
import vn.tsolve.tracker.dto.IssueEventPayload;
import vn.tsolve.tracker.entity.IssueEventType;
import vn.tsolve.tracker.service.IssueEventService;

@Tag(name = "jira-app")
@RestController
@RequestMapping("/api/v1/jira-app/events")
public class JiraAppEventController {

    /** The Jira system token Forge Remote sends with the event, used later by the worker (AC-003.5). */
    public static final String SYSTEM_TOKEN_HEADER = "x-forge-oauth-system";

    private final IssueEventService issueEventService;

    public JiraAppEventController(IssueEventService issueEventService) {
        this.issueEventService = issueEventService;
    }

    @Operation(operationId = "receiveIssueCreated", summary = "Forge trigger avi:jira:created:issue")
    @ApiResponse(responseCode = "400")
    @PreAuthorize("hasRole('" + Roles.JIRA_APP + "')")
    @PostMapping("/issue-created")
    public ResponseEntity<Void> receiveIssueCreated(
            @AuthenticationPrincipal JiraAppPrincipal principal,
            @RequestHeader(SYSTEM_TOKEN_HEADER) String systemToken,
            @Valid @RequestBody IssueEventPayload payload) {
        // Set before the service opens its transaction, so the event is stored in the site's Workspace (AC-003.7).
        WorkspaceContext.runAs(
                principal.workspaceId(),
                () -> issueEventService.receive(
                        principal.cloudId(), IssueEventType.ISSUE_CREATED, payload, systemToken));
        return ResponseEntity.accepted().build();
    }
}
