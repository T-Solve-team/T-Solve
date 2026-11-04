package vn.tsolve.identity.signin;

import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;
import vn.tsolve.identity.signin.port.WorkspaceLookup;
import vn.tsolve.identity.signin.port.WorkspaceRef;

/**
 * Starts a sign-in on a Workspace site or on the Platform Console origin: makes the state value and sends the
 * browser to {@code authorize} on {@code login.} (AC-001.1, AC-001.28). The target comes only from the host.
 */
@RestController
@RequestMapping("/api/v1/auth")
public class SignInStartController {

    private static final Logger log = LoggerFactory.getLogger(SignInStartController.class);

    private final AuthHosts hosts;
    private final WorkspaceLookup workspaces;
    private final SignInStateService states;
    private final SignInProperties properties;

    public SignInStartController(
            AuthHosts hosts, WorkspaceLookup workspaces, SignInStateService states, SignInProperties properties) {
        this.hosts = hosts;
        this.workspaces = workspaces;
        this.states = states;
        this.properties = properties;
    }

    // open to anyone: this is reached before anybody is signed in (AuthSecurityConfig permits the path)
    @PreAuthorize("permitAll()")
    @GetMapping("/sign-in")
    public ResponseEntity<Void> start(@RequestParam(required = false) String returnTo, HttpServletRequest request) {
        if (hosts.isAdminHost(request)) {
            return toAuthorize(states.createForPlatformConsole(returnTo));
        }
        if (hosts.isLoginHost(request)) {
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
        return toAuthorize(states.createForWorkspace(workspace.get().id(), returnTo));
    }

    private ResponseEntity<Void> toAuthorize(String state) {
        URI authorize = UriComponentsBuilder.fromUri(properties.loginOrigin())
                .replacePath("/api/v1/auth/authorize")
                .replaceQuery(null)
                .fragment(null)
                .queryParam("state", state)
                .build()
                .toUri();
        return ResponseEntity.status(HttpStatus.FOUND).location(authorize).build();
    }
}
