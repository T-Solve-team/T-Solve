package vn.tsolve.identity.signin;

import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;
import vn.tsolve.identity.signin.port.WorkspaceLookup;

/**
 * Starts a sign-in on a Workspace site or on the Platform Console origin: makes the state value and sends the
 * browser to {@code authorize} on {@code login.} (AC-001.1, AC-001.28). The target comes only from the host.
 */
@RestController
@RequestMapping("/api/v1/auth")
public class SignInStartController {

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
        Optional<String> state = createState(returnTo, request);
        if (state.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        URI authorize = UriComponentsBuilder.fromUri(properties.loginOrigin())
                .replacePath("/api/v1/auth/authorize")
                .replaceQuery(null)
                .fragment(null)
                .queryParam("state", state.get())
                .build()
                .toUri();
        return ResponseEntity.status(HttpStatus.FOUND).location(authorize).build();
    }

    /** Empty when the host is neither a Workspace site nor the Platform Console origin. */
    private Optional<String> createState(String returnTo, HttpServletRequest request) {
        if (hosts.isAdminHost(request)) {
            return Optional.of(states.createForPlatformConsole(returnTo));
        }
        if (hosts.isLoginHost(request)) {
            return Optional.empty();
        }
        return workspaces
                .findBySiteHost(hosts.hostOf(request))
                .map(workspace -> states.createForWorkspace(workspace.id(), returnTo));
    }
}
