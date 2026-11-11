package vn.tsolve.identity.signin;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
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
import vn.tsolve.identity.signin.port.TenantScope;
import vn.tsolve.identity.signin.port.WorkspaceLookup;
import vn.tsolve.identity.signin.port.WorkspaceRef;

/**
 * Ends the Keycloak callback on a Workspace site (AC-001.6, .7, .8, .9, .10, .11, .14, .53, .55, .74). Every answer
 * expires the auth-request cookie. A failed request check answers MSG141; a wrong Organization or {@code hd} MSG122;
 * a Workspace that is not Active MSG120; a missing email claim or a failed Account step MSG06; no Account MSG09; a
 * deactivated Account MSG10. Only the first failure is reported (AC-001.14). A verified sign-in is shown on the dev
 * claims page in the same request (nothing stored); outside dev it answers MSG06 until the session exists (Task 26a).
 */
@Component
public class OidcCallbackHandler implements AuthenticationSuccessHandler, AuthenticationFailureHandler {

    public static final String DEV_CLAIMS_PATH = "/api/v1/auth/dev/claims";
    public static final String CLAIMS_ATTRIBUTE = "vn.tsolve.identity.signin.claims";

    private static final Logger log = LoggerFactory.getLogger(OidcCallbackHandler.class);
    private static final String MSG141 = "/login?error=MSG141";
    private static final String MSG122 = "/login?error=MSG122";
    private static final String MSG06 = "/login?error=MSG06";
    /** Set once the saved request (state and Workspace) matched; tells Spring's own invalid_request from Keycloak's. */
    public static final String REQUEST_CHECKED_ATTRIBUTE = "vn.tsolve.identity.signin.requestChecked";

    private static final Set<String> REQUEST_CHECK_FAILURES =
            Set.of("authorization_request_not_found", "invalid_state_parameter", "client_registration_not_found");

    private final Environment environment;
    private final WorkspaceLookup workspaces;
    private final AuthHosts hosts;
    private final TenantScope scope;
    private final AccountSignInService accounts;

    public OidcCallbackHandler(
            Environment environment,
            WorkspaceLookup workspaces,
            AuthHosts hosts,
            TenantScope scope,
            AccountSignInService accounts) {
        this.environment = environment;
        this.workspaces = workspaces;
        this.hosts = hosts;
        this.scope = scope;
        this.accounts = accounts;
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
        } else if (environment.acceptsProfiles(Profiles.of("dev"))) {
            Map<String, Object> claims = new LinkedHashMap<>();
            for (String name : new String[] {"sub", "email", "organization", "hd"}) {
                claims.put(name, token.getClaims().get(name));
            }
            request.setAttribute(CLAIMS_ATTRIBUTE, claims);
            expireCookie(response);
            request.getRequestDispatcher(DEV_CLAIMS_PATH).forward(request, response);
        } else {
            log.warn("A Keycloak sign-in succeeded; session not built yet");
            redirect(response, MSG06);
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
