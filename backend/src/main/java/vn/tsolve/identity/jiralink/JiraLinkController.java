package vn.tsolve.identity.jiralink;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.tsolve.common.exception.ProblemDetails;
import vn.tsolve.identity.signin.AuthHosts;
import vn.tsolve.identity.signin.port.WorkspaceLookup;
import vn.tsolve.identity.signin.port.WorkspaceRef;

/**
 * The Jira link confirm page's two calls (AC-001.72, AC-001.75, AC-001.38). The session and the CSRF token are checked
 * by the filters before this runs (401, then 403); a missing, expired, used, other-Workspace or malformed request is
 * 400 MSG141. The Workspace is the one of the host, which the session filter has matched to the session; the tracker
 * account comes only from the stored request. The confirm answers 501 for a valid request until Task 30b.
 */
@RestController
@RequestMapping("/api/v1/auth/jira-link")
public class JiraLinkController {

    private static final String MSG141 = "MSG141";
    // the SRS text of MSG141; the web app's catalog holds the same sentence
    private static final String MSG141_TEXT =
            "This sign-in request is not valid or has expired. Please start the sign-in again.";

    private final JiraLinkRequestService requests;
    private final AuthHosts hosts;
    private final WorkspaceLookup workspaces;
    private final ObjectMapper json;

    public JiraLinkController(
            JiraLinkRequestService requests, AuthHosts hosts, WorkspaceLookup workspaces, ObjectMapper json) {
        this.requests = requests;
        this.hosts = hosts;
        this.workspaces = workspaces;
        this.json = json;
    }

    @GetMapping("/{requestId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> read(@PathVariable String requestId, HttpServletRequest request) {
        return workspaceOf(request)
                .flatMap(workspace -> requests.peek(requestId, workspace))
                .<ResponseEntity<?>>map(found -> ResponseEntity.ok(
                        Map.of("jiraUserName", found.jiraUserName(), "jiraSiteName", found.siteName())))
                .orElseGet(JiraLinkController::invalid);
    }

    @PostMapping("/confirm")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> confirm(@RequestBody(required = false) String body, HttpServletRequest request) {
        Optional<UUID> workspace = workspaceOf(request);
        String requestId = requestIdOf(body);
        // consume runs for every well-formed id, so another Workspace's request is burned (AC-001.72)
        if (workspace.isEmpty() || requests.consume(requestId, workspace.get()).isEmpty()) {
            return invalid();
        }
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build();
    }

    private Optional<UUID> workspaceOf(HttpServletRequest request) {
        return workspaces.findBySiteHost(hosts.hostOf(request)).map(WorkspaceRef::id);
    }

    /** The {@code requestId} text of the body, or null when the body is not JSON or has no text id. */
    private String requestIdOf(String body) {
        if (body == null || body.isBlank()) {
            return null;
        }
        try {
            JsonNode id = json.readTree(body).path("requestId");
            return id.isTextual() ? id.asText() : null;
        } catch (Exception e) {
            return null;
        }
    }

    private static ResponseEntity<ProblemDetail> invalid() {
        ProblemDetail problem = ProblemDetails.withCode(
                ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, MSG141_TEXT), MSG141, Map.of());
        return ResponseEntity.badRequest()
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problem);
    }
}
