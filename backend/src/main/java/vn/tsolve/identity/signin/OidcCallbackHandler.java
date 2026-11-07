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

/**
 * Ends the Keycloak callback on a Workspace site (AC-001.6, .8, .9, .53, .55). Every answer expires the auth-request
 * cookie. A failed request check answers MSG141, any other failure MSG06. A verified sign-in is shown on the dev
 * claims page in the same request (nothing stored); outside dev it answers MSG06 until the session exists (Task 26a).
 */
@Component
public class OidcCallbackHandler implements AuthenticationSuccessHandler, AuthenticationFailureHandler {

    public static final String DEV_CLAIMS_PATH = "/api/v1/auth/dev/claims";
    public static final String CLAIMS_ATTRIBUTE = "vn.tsolve.identity.signin.claims";

    private static final Logger log = LoggerFactory.getLogger(OidcCallbackHandler.class);
    private static final String MSG141 = "/login?error=MSG141";
    private static final String MSG06 = "/login?error=MSG06";
    /** Set once the saved request (state and Workspace) matched; tells Spring's own invalid_request from Keycloak's. */
    public static final String REQUEST_CHECKED_ATTRIBUTE = "vn.tsolve.identity.signin.requestChecked";

    private static final Set<String> REQUEST_CHECK_FAILURES =
            Set.of("authorization_request_not_found", "invalid_state_parameter", "client_registration_not_found");

    private final Environment environment;

    public OidcCallbackHandler(Environment environment) {
        this.environment = environment;
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
