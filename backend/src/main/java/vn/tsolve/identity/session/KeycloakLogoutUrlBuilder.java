package vn.tsolve.identity.session;

import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;
import vn.tsolve.identity.signin.SignInProperties;

/**
 * Builds the Keycloak end-session URL from the session and the request's own origin only (AC-002.1, AC-002.8). The
 * realm and client come from the session's kind; {@code client_id} is always sent, {@code id_token_hint} only when the
 * session has one; {@code post_logout_redirect_uri} is the request's origin plus {@code /login}. Every value is
 * percent-encoded.
 */
@Component
public class KeycloakLogoutUrlBuilder {

    private final SignInProperties properties;

    public KeycloakLogoutUrlBuilder(SignInProperties properties) {
        this.properties = properties;
    }

    public URI build(WebSession session, HttpServletRequest request) {
        boolean operator = session.kind() == WebSession.Kind.OPERATOR;
        Map<String, String> values = new HashMap<>();
        values.put("realm", operator ? "tsolve-ops" : "tsolve");
        values.put("client", operator ? "tsolve-console" : "tsolve-web");
        values.put("redirect", originOf(request) + "/login");
        UriComponentsBuilder builder = UriComponentsBuilder.fromUri(properties.loginOrigin())
                .replacePath("/realms/{realm}/protocol/openid-connect/logout")
                .replaceQuery(null)
                .fragment(null)
                .queryParam("client_id", "{client}");
        if (session.idTokenHint() != null && !session.idTokenHint().isEmpty()) {
            builder.queryParam("id_token_hint", "{hint}");
            values.put("hint", session.idTokenHint());
        }
        builder.queryParam("post_logout_redirect_uri", "{redirect}");
        // encode() on the builder percent-encodes each expanded value strictly (&, #, +, space, ...)
        return builder.encode().build(values);
    }

    private static String originOf(HttpServletRequest request) {
        String scheme = request.getScheme();
        int port = request.getServerPort();
        boolean defaultPort =
                ("http".equalsIgnoreCase(scheme) && port == 80) || ("https".equalsIgnoreCase(scheme) && port == 443);
        return scheme + "://" + request.getServerName() + (defaultPort || port <= 0 ? "" : ":" + port);
    }
}
