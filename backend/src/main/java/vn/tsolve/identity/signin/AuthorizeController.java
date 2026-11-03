package vn.tsolve.identity.signin;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Optional;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vn.tsolve.identity.signin.port.WorkspaceLookup;
import vn.tsolve.identity.signin.port.WorkspaceRef;

/**
 * Sends a valid state to Google's authorization endpoint. Answers only on {@code login.} (AC-001.5); a missing,
 * edited, expired or used state gets the minimal MSG141 page (AC-001.6). The state is bound to the browser here, once
 * (the sign-in cookie, AC-001.44); it is not used up: the callback consumes it.
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthorizeController {

    private static final String MSG_INVALID_REQUEST = "MSG141";

    private final AuthHosts hosts;
    private final SignInStateService states;
    private final WorkspaceLookup workspaces;
    private final GoogleOidcClient google;
    private final SignInCookie cookie;

    public AuthorizeController(
            AuthHosts hosts,
            SignInStateService states,
            WorkspaceLookup workspaces,
            GoogleOidcClient google,
            SignInCookie cookie) {
        this.hosts = hosts;
        this.states = states;
        this.workspaces = workspaces;
        this.google = google;
        this.cookie = cookie;
    }

    // open to anyone: the browser arrives here from a redirect with no session (AuthSecurityConfig permits the path)
    @PreAuthorize("permitAll()")
    @GetMapping("/authorize")
    public ResponseEntity<String> authorize(@RequestParam(required = false) String state, HttpServletRequest request) {
        if (!hosts.isLoginHost(request)) {
            return ResponseEntity.notFound().build();
        }
        Optional<SignInState> checked = states.check(state);
        if (checked.isEmpty()) {
            return invalidRequest();
        }
        SignInState found = checked.get();
        // hd for a Workspace or Jira-link target, never without one (AC-001.49); the Platform Console has none
        // (AC-001.28)
        String hostedDomain = null;
        if (found.getTarget() != SignInState.Target.PLATFORM_CONSOLE) {
            Optional<WorkspaceRef> workspace = workspaces.findById(found.getWorkspaceId());
            if (workspace.isEmpty() || isBlank(workspace.get().googleDomain())) {
                return invalidRequest();
            }
            hostedDomain = workspace.get().googleDomain();
        }
        // binds the state to this browser, once; a second authorize of the same state finds it taken (AC-001.45)
        String cookieValue = cookie.newValue();
        if (states.authorizeOnce(state, cookie.hash(cookieValue)).isEmpty()) {
            return invalidRequest();
        }
        return ResponseEntity.status(HttpStatus.FOUND)
                .location(google.authorizationUrl(state, found, hostedDomain))
                .header(HttpHeaders.SET_COOKIE, cookie.issue(cookieValue).toString())
                .cacheControl(CacheControl.noStore())
                .build();
    }

    private static boolean isBlank(String text) {
        return text == null || text.isBlank();
    }

    private static ResponseEntity<String> invalidRequest() {
        return LoginPage.response(HttpStatus.BAD_REQUEST, MSG_INVALID_REQUEST);
    }
}
