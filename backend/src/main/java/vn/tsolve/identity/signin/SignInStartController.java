package vn.tsolve.identity.signin;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.net.URI;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.oauth2.client.web.AuthorizationRequestRepository;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vn.tsolve.identity.signin.port.WorkspaceLookup;
import vn.tsolve.identity.signin.port.WorkspaceRef;

/**
 * Starts a sign-in on a Workspace site. The resolver builds the Keycloak authorization request, it is stored in Redis,
 * and the browser is redirected to Keycloak (AC-001.1, AC-001.52). On the {@code admin.} host nothing starts and the
 * answer is 404 until the Platform Console sign-in (Task 29). The target comes only from the host.
 */
@RestController
@RequestMapping("/api/v1/auth")
public class SignInStartController {

    private static final Logger log = LoggerFactory.getLogger(SignInStartController.class);
    private static final String DEFAULT_RETURN_TO = "/";
    private static final int MAX_RETURN_TO_LENGTH = 2048;

    private final AuthHosts hosts;
    private final WorkspaceLookup workspaces;

    private final KeycloakAuthorizationRequestResolver resolver;
    private final AuthorizationRequestRepository<OAuth2AuthorizationRequest> authorizationRequests;

    public SignInStartController(
            AuthHosts hosts,
            WorkspaceLookup workspaces,
            KeycloakAuthorizationRequestResolver resolver,
            AuthorizationRequestRepository<OAuth2AuthorizationRequest> authorizationRequests) {
        this.hosts = hosts;
        this.workspaces = workspaces;
        this.resolver = resolver;
        this.authorizationRequests = authorizationRequests;
    }

    // open to anyone: this is reached before anybody is signed in (AuthSecurityConfig permits the path)
    @PreAuthorize("permitAll()")
    @GetMapping("/sign-in")
    public ResponseEntity<Void> start(
            @RequestParam(required = false) String returnTo, HttpServletRequest request, HttpServletResponse response) {
        if (hosts.isAdminHost(request)) {
            return ResponseEntity.notFound().build();
        }
        Optional<WorkspaceRef> workspace = workspaces.findBySiteHost(hosts.hostOf(request));
        if (workspace.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        String domain = workspace.get().googleDomain();
        if (domain == null || domain.isBlank()) {
            // AC-001.48: no state, no Google; the Workspace's own Login page shows MSG141. Only the id is logged.
            log.warn(
                    "Sign-in refused: Workspace {} has no Google domain",
                    workspace.get().id());
            return ResponseEntity.status(HttpStatus.FOUND)
                    .location(URI.create("/login?error=MSG141"))
                    .build();
        }
        OAuth2AuthorizationRequest authorization = resolver.resolve(request, workspace.get(), filterReturnTo(returnTo));
        authorizationRequests.saveAuthorizationRequest(authorization, request, response);
        return ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create(authorization.getAuthorizationRequestUri()))
                .build();
    }

    /**
     * Keeps {@code returnTo} only when it is a path of the same site: one leading slash, no backslash, no ASCII
     * control or space character, at most 2,048 characters (AC-001.3). Anything else becomes {@code /}.
     */
    static String filterReturnTo(String returnTo) {
        if (returnTo == null
                || returnTo.isEmpty()
                || returnTo.length() > MAX_RETURN_TO_LENGTH
                || returnTo.charAt(0) != '/'
                || (returnTo.length() > 1 && returnTo.charAt(1) == '/')) {
            return DEFAULT_RETURN_TO;
        }
        for (int i = 0; i < returnTo.length(); i++) {
            char c = returnTo.charAt(i);
            if (c <= ' ' || c == 0x7F || c == '\\') {
                return DEFAULT_RETURN_TO;
            }
        }
        return returnTo;
    }
}
