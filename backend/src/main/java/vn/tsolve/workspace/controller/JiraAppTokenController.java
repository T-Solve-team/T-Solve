package vn.tsolve.workspace.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.tsolve.common.security.JiraAppPrincipal;
import vn.tsolve.common.security.Roles;
import vn.tsolve.workspace.service.JiraAppTokenService;

/** The Jira App hands its app token to the backend (workspace STORY-012, jira-app STORY-013, KAN-349). */
@RestController
@RequestMapping("/api/v1/jira-app/app-token")
@Tag(name = "jira-app")
public class JiraAppTokenController {

    private final JiraAppTokenService tokens;

    public JiraAppTokenController(JiraAppTokenService tokens) {
        this.tokens = tokens;
    }

    @PostMapping
    @PreAuthorize("hasRole('" + Roles.JIRA_APP + "')")
    @Operation(operationId = "receiveJiraAppToken", summary = "Hold the Jira App's app system token for its Workspace")
    @ApiResponse(responseCode = "400", description = "MSG06: no usable token or a foreign API base")
    @ApiResponse(responseCode = "403", description = "MSG08: the site belongs to no Workspace")
    public ResponseEntity<Void> receiveJiraAppToken(
            @AuthenticationPrincipal JiraAppPrincipal caller,
            @RequestHeader(name = "x-forge-oauth-system", required = false) String token) {
        tokens.receive(caller.cloudId(), caller.apiBaseUrl(), token);
        return ResponseEntity.noContent().build();
    }
}
