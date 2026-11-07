package vn.tsolve.identity.signin;

import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames;
import org.springframework.security.oauth2.core.endpoint.PkceParameterNames;
import org.springframework.security.oauth2.core.oidc.endpoint.OidcParameterNames;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import vn.tsolve.identity.signin.port.WorkspaceRef;

/**
 * Builds the Keycloak authorization request for a Workspace (AC-001.1, AC-001.52): S256 PKCE, a hashed nonce in the
 * URL, the Identity Provider hint from the Workspace, and a {@code redirect_uri} from the request's own origin. The
 * verifier, the raw nonce, the target, the Workspace id and the kept returnTo stay in the request's attributes.
 */
@Component
public class KeycloakAuthorizationRequestResolver {

    public static final String TARGET = "target";
    public static final String WORKSPACE_ID = "workspace_id";
    public static final String RETURN_TO = "return_to";

    private static final String CALLBACK_PATH = "/api/v1/auth/callback";
    private static final int RANDOM_BYTES = 32;

    private final ClientRegistrationRepository registrations;
    private final SecureRandom random = new SecureRandom();

    public KeycloakAuthorizationRequestResolver(ClientRegistrationRepository registrations) {
        this.registrations = registrations;
    }

    public OAuth2AuthorizationRequest resolve(HttpServletRequest request, WorkspaceRef workspace, String keptReturnTo) {
        ClientRegistration registration =
                registrations.findByRegistrationId(KeycloakClientRegistrations.REGISTRATION_ID);
        String verifier = randomValue();
        String rawNonce = randomValue();

        Map<String, Object> attributes = new HashMap<>();
        attributes.put(OAuth2ParameterNames.REGISTRATION_ID, registration.getRegistrationId());
        attributes.put(PkceParameterNames.CODE_VERIFIER, verifier);
        attributes.put(OidcParameterNames.NONCE, rawNonce);
        attributes.put(TARGET, "WORKSPACE");
        attributes.put(WORKSPACE_ID, workspace.id().toString());
        attributes.put(RETURN_TO, keptReturnTo);

        Map<String, Object> parameters = new HashMap<>();
        parameters.put(OidcParameterNames.NONCE, sha256(rawNonce));
        parameters.put(PkceParameterNames.CODE_CHALLENGE, sha256(verifier));
        parameters.put(PkceParameterNames.CODE_CHALLENGE_METHOD, "S256");
        parameters.put("kc_idp_hint", "google-" + workspace.slug());

        // scheme, host and port come from the request (ForwardedHeaderFilter applies); no query, default port dropped
        String redirectUri = ServletUriComponentsBuilder.fromContextPath(request)
                .path(CALLBACK_PATH)
                .build()
                .toUriString();

        return OAuth2AuthorizationRequest.authorizationCode()
                .authorizationUri(registration.getProviderDetails().getAuthorizationUri())
                .clientId(registration.getClientId())
                .redirectUri(redirectUri)
                .scopes(registration.getScopes())
                .state(randomValue())
                .additionalParameters(parameters)
                .attributes(attributes)
                .build();
    }

    private String randomValue() {
        byte[] bytes = new byte[RANDOM_BYTES];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.US_ASCII));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }
}
