package vn.tsolve.identity.signin;

import java.net.URI;
import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import vn.tsolve.identity.dev.DevAccounts;

/** Shared values and helpers for the sign-in tests. */
public final class SignInTestFixtures {

    /** Not the production origins; the port proves the URLs come from config. */
    public static final String LOGIN_ORIGIN = "http://login.localhost:8081";

    public static final String ADMIN_ORIGIN = "http://admin.localhost:8081";

    /**
     * Base64 of 32 zero bytes. The web process no longer reads a signing key; tests still pass it as an unused
     * property, and it is kept here so those property lists stay as they are.
     */
    public static final String KEY = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=";

    public static final String ID_TOKEN_VALUE = "id-token-value-should-never-appear";
    public static final String NONCE_VALUE = "nonce-value-should-never-appear";

    private SignInTestFixtures() {}

    /** A signed-in token holding these ID token claims (iss, aud and nonce are added when absent). */
    public static OAuth2AuthenticationToken signedIn(Map<String, Object> claims) {
        Map<String, Object> all = new HashMap<>(claims);
        all.putIfAbsent("iss", "http://login.localhost:8080/realms/tsolve");
        all.putIfAbsent("aud", List.of("tsolve-web"));
        all.putIfAbsent("nonce", NONCE_VALUE);
        Instant now = Instant.now();
        OidcIdToken idToken = new OidcIdToken(ID_TOKEN_VALUE, now, now.plusSeconds(300), all);
        DefaultOidcUser user = new DefaultOidcUser(List.of(new SimpleGrantedAuthority("OIDC_USER")), idToken);
        return new OAuth2AuthenticationToken(user, user.getAuthorities(), KeycloakClientRegistrations.REGISTRATION_ID);
    }

    /** Test-only: the {@link DevAccounts} as Active, sub-less {@code app_user} rows (DevAccountSeeder is Task 26b). */
    public static void seedDevAccounts(JdbcTemplate jdbc) {
        for (DevAccounts.DevAccount account : DevAccounts.all()) {
            seedAccount(jdbc, account.email(), account.displayName());
        }
    }

    public static void seedAccount(JdbcTemplate jdbc, String email, String fullName) {
        jdbc.update(
                "INSERT INTO app_user (email, full_name, status, created_at) VALUES (?, ?, 'ACTIVE', now())"
                        + " ON CONFLICT (email) DO NOTHING",
                email,
                fullName);
    }

    public static SignInProperties properties() {
        return new SignInProperties(
                URI.create(LOGIN_ORIGIN),
                URI.create(ADMIN_ORIGIN),
                "ops@example.test",
                "test-keycloak-secret",
                "test-keycloak-console-secret");
    }

    /** Every Set-Cookie header value whose cookie has this name. */
    public static List<String> setCookie(MockHttpServletResponse response, String name) {
        return response.getHeaders(HttpHeaders.SET_COOKIE).stream()
                .filter(header -> header.startsWith(name + "="))
                .toList();
    }

    /** The attributes of one Set-Cookie value, lower-cased names; a flag has an empty value. */
    public static Map<String, String> attributes(String setCookie) {
        Map<String, String> attributes = new LinkedHashMap<>();
        String[] parts = setCookie.split(";\\s*");
        for (int i = 1; i < parts.length; i++) {
            int eq = parts[i].indexOf('=');
            String name = (eq < 0 ? parts[i] : parts[i].substring(0, eq)).toLowerCase(java.util.Locale.ROOT);
            attributes.put(name, eq < 0 ? "" : parts[i].substring(eq + 1));
        }
        return attributes;
    }
}
