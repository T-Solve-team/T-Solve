package vn.tsolve.tracker.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.tsolve.common.security.Roles;
import vn.tsolve.tracker.dto.IssueCreatedPayload;
import vn.tsolve.tracker.service.IssueCreatedService;

@Tag(name = "jira-app")
@RestController
@RequestMapping("/api/v1/jira-app/events")
public class JiraAppEventController {

    private final IssueCreatedService issueCreatedService;

    public JiraAppEventController(IssueCreatedService issueCreatedService) {
        this.issueCreatedService = issueCreatedService;
    }

    @Operation(operationId = "receiveIssueCreated", summary = "Forge trigger avi:jira:created:issue")
    @ApiResponse(responseCode = "400")
    @PreAuthorize("hasRole('" + Roles.JIRA_APP + "')")
    @PostMapping("/issue-created")
    public ResponseEntity<Void> receiveIssueCreated(@Valid @RequestBody IssueCreatedPayload payload) {
        issueCreatedService.receive(payload);
        return ResponseEntity.accepted().build();
    }
}
