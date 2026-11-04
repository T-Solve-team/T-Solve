package vn.tsolve.identity.signin;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.net.URI;
import java.util.Optional;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;
import vn.tsolve.identity.account.AccountSignInService;
import vn.tsolve.identity.signin.port.TenantScope;
import vn.tsolve.identity.signin.port.WorkspaceLookup;
import vn.tsolve.identity.signin.port.WorkspaceRef;

/**
 * Google's redirect target on the central sign-in origin (AC-001.5). The checks run in this order: state value, the
 * browser's sign-in cookie, the state's Workspace, Google's answer. Whatever the outcome, the sign-in cookie is
 * expired (AC-001.47) and the state is used up. A Workspace sign-in then checks, in this order and reporting only
 * the first failure (AC-001.14): Google's answer, Workspace Active, the {@code hd} claim, the Account in the
 * Workspace, Account Active. The Platform Console and the Jira link come with later tasks; until then they get the
 * MSG141 page.
 */
@RestController
@RequestMapping("/api/v1/auth")
public class CallbackController {

    private static final String MSG_INVALID_REQUEST = "MSG141";

    private final AuthHosts hosts;
    private final SignInStateService states;
    private final WorkspaceLookup workspaces;
    private final GoogleOidcClient google;
    private final IdTokenVerifier idTokens;
    private final SignInCookie cookie;
    private final SignInProperties properties;
    private final TenantScope tenantScope;
    private final AccountSignInService accountSignIn;
    private final HandOffCodeService handOffCodes;

    public CallbackController(
            AuthHosts hosts,
            SignInStateService states,
            WorkspaceLookup workspaces,
            GoogleOidcClient google,
            IdTokenVerifier idTokens,
            SignInCookie cookie,
            SignInProperties properties,
            TenantScope tenantScope,
            AccountSignInService accountSignIn,
            HandOffCodeService handOffCodes) {
        this.hosts = hosts;
        this.states = states;
        this.workspaces = workspaces;
        this.google = google;
        this.idTokens = idTokens;
        this.cookie = cookie;
        this.properties = properties;
        this.tenantScope = tenantScope;
        this.accountSignIn = accountSignIn;
        this.handOffCodes = handOffCodes;
    }

    // open to anyone: this is reached before anybody is signed in
    @PreAuthorize("permitAll()")
    @GetMapping("/callback")
    public ResponseEntity<String> callback(
            @RequestParam(required = false) String code,
            @RequestParam(required = false) String error,
            @RequestParam(required = false) String state,
            HttpServletRequest request,
            HttpServletResponse response) {
        if (!hosts.isLoginHost(request)) {
            return ResponseEntity.notFound().build();
        }
        // first, so that every answer below, a 500 included, carries it (AC-001.47)
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.expire().toString());

        Optional<SignInState> consumed = states.consume(state);
        if (consumed.isEmpty()) {
            return invalidRequest();
        }
        SignInState found = consumed.get();
        // a state that was never authorized has no hash and never matches (AC-001.46)
        if (!cookie.matches(signInCookieOf(request), found.getBrowserBindingHash())) {
            return invalidRequest();
        }
        boolean needsWorkspace = found.getTarget() != SignInState.Target.PLATFORM_CONSOLE;
        Optional<WorkspaceRef> workspace =
                needsWorkspace ? workspaces.findById(found.getWorkspaceId()) : Optional.empty();
        if (needsWorkspace && (workspace.isEmpty() || isBlank(workspace.get().googleDomain()))) {
            return invalidRequest();
        }
        Optional<GoogleIdentity> identity = googleAccepts(code, error, found);
        if (found.getTarget() != SignInState.Target.WORKSPACE) {
            // interim: Platform Console (T11) and Jira link (T12) get their real outcome later
            return invalidRequest();
        }
        return signInToWorkspace(workspace.get(), identity, found);
    }

    private ResponseEntity<String> signInToWorkspace(
            WorkspaceRef workspace, Optional<GoogleIdentity> identity, SignInState found) {
        if (identity.isEmpty()) {
            return redirectToWorkspaceLogin(workspace, "MSG06", null);
        }
        if (workspace.status() != WorkspaceRef.Status.ACTIVE) {
            return redirectToWorkspaceLogin(workspace, "MSG120", null);
        }
        GoogleIdentity google = identity.get();
        if (google.hostedDomain() == null || !google.hostedDomain().equalsIgnoreCase(workspace.googleDomain())) {
            return redirectToWorkspaceLogin(workspace, "MSG122", workspace.googleDomain());
        }
        // the transaction of signIn opens inside runIn, so it reaches the Workspace's own database only
        AccountSignInService.Result result =
                tenantScope.runIn(workspace.id(), () -> accountSignIn.signIn(google.email(), google.name()));
        if (result.status() == AccountSignInService.Status.NO_ACCOUNT) {
            return redirectToWorkspaceLogin(workspace, "MSG09", null);
        }
        if (result.status() == AccountSignInService.Status.DEACTIVATED) {
            return redirectToWorkspaceLogin(workspace, "MSG10", null);
        }
        // the sign-in time stays recorded when issuing fails: the code is in another database (edge 42)
        String handOff =
                handOffCodes.issue(workspace.id(), result.accountId(), workspace.siteHost(), found.getReturnTo());
        URI complete = workspaceSite(workspace, "/api/v1/auth/complete")
                .queryParam("code", handOff)
                .build()
                .encode()
                .toUri();
        return ResponseEntity.status(HttpStatus.FOUND).location(complete).build();
    }

    /** The identity when Google sent no error and a code that gave an ID token for this state's nonce. */
    private Optional<GoogleIdentity> googleAccepts(String code, String error, SignInState found) {
        if (error != null || code == null) {
            return Optional.empty();
        }
        return google.exchangeCode(code, found.getPkceVerifier())
                .flatMap(idToken -> idTokens.verify(idToken, found.getNonce()));
    }

    /** The Workspace's login page with a message code, and the Google domain for MSG122 (AC-001.11). */
    private ResponseEntity<String> redirectToWorkspaceLogin(
            WorkspaceRef workspace, String msgCode, String googleDomain) {
        UriComponentsBuilder login = workspaceSite(workspace, "/login").queryParam("error", msgCode);
        if (googleDomain != null) {
            login.queryParam("google_domain", googleDomain);
        }
        return ResponseEntity.status(HttpStatus.FOUND)
                .location(login.build().encode().toUri())
                .build();
    }

    /** The Workspace's own site: scheme and port of the sign-in origin, the Workspace's host (never from the request). */
    private UriComponentsBuilder workspaceSite(WorkspaceRef workspace, String path) {
        return UriComponentsBuilder.fromUri(properties.loginOrigin())
                .host(workspace.siteHost())
                .replacePath(path)
                .replaceQuery(null)
                .fragment(null);
    }

    /** The cookie value as sent. Not {@code @CookieValue}: it URL-decodes and throws (a 500) on a value like {@code %$}. */
    private static String signInCookieOf(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie candidate : cookies) {
                if (SignInCookie.NAME.equals(candidate.getName())) {
                    return candidate.getValue();
                }
            }
        }
        return null;
    }

    private static ResponseEntity<String> invalidRequest() {
        return LoginPage.response(HttpStatus.BAD_REQUEST, MSG_INVALID_REQUEST);
    }

    private static boolean isBlank(String text) {
        return text == null || text.isBlank();
    }
}
