package vn.tsolve.identity.signin;

import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.stereotype.Component;

/**
 * The {@code tsolve-web} registration, built in code from {@link SignInProperties}. It sets {@code issuerUri} so the ID
 * token's {@code iss} is checked, and never reads discovery, so Keycloak being down does not stop the start-up.
 */
@Component
public class KeycloakClientRegistrations implements ClientRegistrationRepository {

    public static final String REGISTRATION_ID = "tsolve-web";
    public static final String CONSOLE_REGISTRATION_ID = "tsolve-console";

    private final InMemoryClientRegistrationRepository delegate;

    public KeycloakClientRegistrations(SignInProperties properties) {
        String loginOrigin = trimSlash(properties.loginOrigin().toString());
        String adminCallback = trimSlash(properties.adminOrigin().toString()) + "/api/v1/auth/callback";
        this.delegate = new InMemoryClientRegistrationRepository(
                registration(
                        REGISTRATION_ID,
                        loginOrigin + "/realms/tsolve",
                        properties.keycloakWebClientSecret(),
                        "{baseUrl}/api/v1/auth/callback"),
                registration(
                        CONSOLE_REGISTRATION_ID,
                        loginOrigin + "/realms/tsolve-ops",
                        properties.keycloakConsoleClientSecret(),
                        adminCallback));
    }

    private static ClientRegistration registration(String id, String issuer, String secret, String redirectUri) {
        String oidc = issuer + "/protocol/openid-connect";
        return ClientRegistration.withRegistrationId(id)
                .clientId(id)
                .clientSecret(secret)
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri(redirectUri)
                .scope("openid", "email", "profile")
                .authorizationUri(oidc + "/auth")
                .tokenUri(oidc + "/token")
                .jwkSetUri(oidc + "/certs")
                .issuerUri(issuer)
                .userNameAttributeName("sub")
                .build();
    }

    @Override
    public ClientRegistration findByRegistrationId(String registrationId) {
        return delegate.findByRegistrationId(registrationId);
    }

    private static String trimSlash(String origin) {
        return origin.endsWith("/") ? origin.substring(0, origin.length() - 1) : origin;
    }
}
