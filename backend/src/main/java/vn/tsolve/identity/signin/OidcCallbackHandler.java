package vn.tsolve.identity.signin;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import vn.tsolve.identity.account.AccountSignInService;
import vn.tsolve.identity.session.SessionCookie;
import vn.tsolve.identity.session.SessionEnded;
import vn.tsolve.identity.session.WebSession;
import vn.tsolve.identity.session.WebSessionStore;
import vn.tsolve.identity.signin.port.TenantScope;
import vn.tsolve.identity.signin.port.WorkspaceLookup;
import vn.tsolve.identity.signin.port.WorkspaceRef;

/**
 * Ends the Keycloak callback on a Workspace site (AC-001.6, .7, .8, .9, .10, .11, .14, .53, .55, .74). Every answer
 * expires the auth-request cookie. A failed request check answers MSG141; a wrong Organization or {@code hd} MSG122;
 * a Workspace that is not Active MSG120; a missing email claim or a failed Account step MSG06; no Account MSG09; a
 * deactivated Account MSG10. Only the first failure is reported (AC-001.14). A passing sign-in creates the session
 * at once (AC-001.16, .18): a 302 to the kept {@code return_to} (or {@code /}) with {@code __Host-TSOLVE_SESSION}; a
 * failing session store answers MSG06 and creates no session.
 */
@Component
public class OidcCallbackHandler implements AuthenticationSuccessHandler, AuthenticationFailureHandler {

    private static final Logger log = LoggerFactory.getLogger(OidcCallbackHandler.class);
    private static final String MSG141 = "/login?error=MSG141";
    private static final String MSG122 = "/login?error=MSG122";
    private static final String MSG06 = "/login?error=MSG06";
    /** Set once the saved request (state and Workspace) matched; tells Spring's own invalid_request from Keycloak's. */
    public static final String REQUEST_CHECKED_ATTRIBUTE = "vn.tsolve.identity.signin.requestChecked";

    private static final Set<String> REQUEST_CHECK_FAILURES =
            Set.of("authorization_request_not_found", "invalid_state_parameter", "client_registration_not_found");

    /**
     * The request attribute that carries the kept {@code return_to} from {@code
     * RedisAuthorizationRequestRepository.removeAuthorizationRequest} to this handler.
     */
    public static final String RETURN_TO_ATTRIBUTE = "vn.tsolve.identity.signin.returnTo";

    private final WorkspaceLookup workspaces;
    private final AuthHosts hosts;
    private final TenantScope scope;
    private final AccountSignInService accounts;
    private final WebSessionStore sessions;
    private final SessionCookie sessionCookies;
    private final IdentityClock clock;
    private final ApplicationEventPublisher events;

    /** Without an event publisher (unit tests of the checks): a replaced session publishes nowhere. */
    public OidcCallbackHandler(
            WorkspaceLookup workspaces,
            AuthHosts hosts,
            TenantScope scope,
            AccountSignInService accounts,
            WebSessionStore sessions,
            SessionCookie sessionCookies,
            IdentityClock clock) {
        this(workspaces, hosts, scope, accounts, sessions, sessionCookies, clock, event -> {});
    }

    @Autowired
    public OidcCallbackHandler(
            WorkspaceLookup workspaces,
            AuthHosts hosts,
            TenantScope scope,
            AccountSignInService accounts,
            WebSessionStore sessions,
            SessionCookie sessionCookies,
            IdentityClock clock,
            ApplicationEventPublisher events) {
        this.events = events;
        this.workspaces = workspaces;
        this.hosts = hosts;
        this.scope = scope;
        this.accounts = accounts;
        this.sessions = sessions;
        this.sessionCookies = sessionCookies;
        this.clock = clock;
    }

    @Override
    public void onAuthenticationFailure(
            HttpServletRequest request, HttpServletResponse response, AuthenticationException exception) {
        boolean requestCheck = exception instanceof OAuth2AuthenticationException oauth2
                && (REQUEST_CHECK_FAILURES.contains(oauth2.getError().getErrorCode())
                        || ("invalid_request".equals(oauth2.getError().getErrorCode())
                                && request.getAttribute(REQUEST_CHECKED_ATTRIBUTE) == null));
        redirect(response, requestCheck ? MSG141 : MSG06);
    }

    @Override
    public void onAuthenticationSuccess(
            HttpServletRequest request, HttpServletResponse response, Authentication authentication)
            throws IOException, ServletException {
        OidcIdToken token = ((OidcUser) authentication.getPrincipal()).getIdToken();
        if (!Boolean.TRUE.equals(token.getEmailVerified())) {
            redirect(response, MSG06);
            return;
        }
        // AC-001.7: the Workspace comes only from the host
        WorkspaceRef workspace =
                workspaces.findBySiteHost(hosts.hostOf(request)).orElse(null);
        if (workspace == null) {
            redirect(response, MSG141);
            return;
        }
        // AC-001.14 order: Organization and hd, Workspace Active, Account, Account Active; first failure only
        if (!namesOrganization(token.getClaims().get("organization"), workspace.organizationId())
                || !matchesHd(token.getClaims().get("hd"), workspace.googleDomain())) {
            String domain = workspace.googleDomain();
            redirect(response, domain == null || domain.isBlank() ? MSG122 : MSG122 + "&google_domain=" + domain);
            return;
        }
        if (workspace.status() != WorkspaceRef.Status.ACTIVE) {
            redirect(response, "/login?error=MSG120");
            return;
        }
        String email = token.getEmail();
        if (email == null || email.isBlank()) {
            // AC-001.74: a verified token without an email claim fails the Account step, even for a bound sub
            log.error("Sign-in failed in the Account step of Workspace {} (no email claim)", workspace.id());
            redirect(response, MSG06);
            return;
        }
        AccountSignInService.Result result;
        try {
            // outside any transaction: signIn opens its own
            result = scope.runIn(
                    workspace.id(), () -> accounts.signIn(token.getSubject(), email, token.getClaimAsString("name")));
        } catch (RuntimeException e) {
            // AC-001.74: the exception text can hold the email, sub and name, so only class names are logged
            Throwable root = e;
            while (root.getCause() != null && root.getCause() != root) {
                root = root.getCause();
            }
            log.error(
                    "Sign-in failed in the Account step of Workspace {} ({}, root cause {})",
                    workspace.id(),
                    e.getClass().getName(),
                    root.getClass().getName());
            redirect(response, MSG06);
            return;
        }
        if (result.status() == AccountSignInService.Status.DEACTIVATED) {
            redirect(response, "/login?error=MSG10");
        } else if (result.status() != AccountSignInService.Status.SIGNED_IN) {
            redirect(response, "/login?error=MSG09");
        } else {
            startSession(request, response, workspace, result.accountId(), token.getTokenValue());
        }
    }

    /** AC-001.16, .18: a new session id for every sign-in (any old one is ended), then the 302 that sets its cookie. */
    private void startSession(
            HttpServletRequest request,
            HttpServletResponse response,
            WorkspaceRef workspace,
            UUID accountId,
            String idToken) {
        String value;
        try {
            String old = sessionCookies.valueOf(request);
            if (old != null && !old.isEmpty()) {
                replaceOld(old);
            }
            Instant now = clock.now();
            value = sessions.create(new WebSession(
                    WebSession.Kind.WORKSPACE,
                    workspace.id(),
                    accountId,
                    null,
                    workspace.siteHost(),
                    idToken,
                    now,
                    now));
        } catch (DataAccessException e) {
            // class name only: no ID token, cookie value or hash may reach the log
            log.error(
                    "Sign-in passed but the session store (Redis) failed for Workspace {} ({})",
                    workspace.id(),
                    e.getClass().getName());
            redirect(response, MSG06);
            return;
        }
        response.addHeader(HttpHeaders.SET_COOKIE, sessionCookies.issue(value).toString());
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        Object returnTo = request.getAttribute(RETURN_TO_ATTRIBUTE);
        redirect(response, SignInStartController.filterReturnTo(returnTo instanceof String s ? s : null));
    }

    /**
     * Ends the old session of the same browser. A live Workspace session that this call ends publishes one LOGOUT
     * (AC-002.3); a session already past a limit loses only its key, so the sweep publishes its EXPIRED (AC-002.4);
     * an Operator session publishes nothing.
     */
    private void replaceOld(String old) {
        WebSession prior = sessions.find(old).orElse(null);
        if (prior != null && !sessions.isWithinLimits(prior, clock.now())) {
            sessions.deleteKeyOnly(old);
            return;
        }
        boolean ended = sessions.end(old);
        if (ended
                && prior != null
                && prior.kind() == WebSession.Kind.WORKSPACE
                && prior.workspaceId() != null
                && prior.accountId() != null) {
            try {
                events.publishEvent(new SessionEnded(
                        prior.accountId(), prior.workspaceId(), sessions.sessionIdOf(old), SessionEnded.Reason.LOGOUT));
            } catch (RuntimeException e) {
                // class name only: a listener's message can hold personal data
                log.error(
                        "A SessionEnded listener failed on a replaced session ({})",
                        e.getClass().getName());
            }
        }
    }

    /** True when some entry of the organization claim map has an {@code id} equal to the Workspace's (Q1 A). */
    private static boolean namesOrganization(Object claim, String organizationId) {
        if (organizationId == null || organizationId.isBlank() || !(claim instanceof Map<?, ?> entries)) {
            return false;
        }
        return entries.values().stream()
                .anyMatch(entry -> entry instanceof Map<?, ?> map && organizationId.equals(map.get("id")));
    }

    private static boolean matchesHd(Object hd, String domain) {
        return domain != null && !domain.isBlank() && hd instanceof String value && value.equalsIgnoreCase(domain);
    }

    private static void redirect(HttpServletResponse response, String location) {
        expireCookie(response);
        response.setStatus(HttpServletResponse.SC_FOUND);
        response.setHeader(HttpHeaders.LOCATION, location);
    }

    private static void expireCookie(HttpServletResponse response) {
        response.addHeader(
                HttpHeaders.SET_COOKIE,
                RedisAuthorizationRequestRepository.COOKIE_NAME
                        + "=; Path=/; Max-Age=0; Secure; HttpOnly; SameSite=Lax");
    }
}
