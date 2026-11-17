package vn.tsolve.jiraapp.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.tsolve.common.security.JiraAppPrincipal;
import vn.tsolve.common.security.Roles;
import vn.tsolve.jiraapp.dto.JiraAccountLinkStart;
import vn.tsolve.jiraapp.dto.JiraAccountLinkStatus;
import vn.tsolve.jiraapp.service.JiraAccountLinkPageService;

@RestController
@RequestMapping("/api/v1/jira-app/account-link")
@Tag(name = "jira-app")
public class JiraAccountLinkController {

    private final JiraAccountLinkPageService links;

    public JiraAccountLinkController(JiraAccountLinkPageService links) {
        this.links = links;
    }

    @GetMapping
    @PreAuthorize("hasRole('" + Roles.JIRA_APP + "')")
    @Operation(operationId = "getJiraAccountLink", summary = "Whether the Jira user is linked to a T-Solve Account")
    @ApiResponse(responseCode = "403", description = "MSG08")
    public ResponseEntity<JiraAccountLinkStatus> getJiraAccountLink(@AuthenticationPrincipal JiraAppPrincipal caller) {
        return ResponseEntity.ok(links.status(caller));
    }

    @PostMapping
    @PreAuthorize("hasRole('" + Roles.JIRA_APP + "')")
    @Operation(operationId = "startJiraAccountLink", summary = "Starts linking the Jira user to a T-Solve Account")
    @ApiResponse(responseCode = "403", description = "MSG124 or MSG08")
    @ApiResponse(responseCode = "503", description = "MSG06")
    public ResponseEntity<JiraAccountLinkStart> startJiraAccountLink(@AuthenticationPrincipal JiraAppPrincipal caller) {
        return ResponseEntity.ok(links.start(caller));
    }
}
