package vn.tsolve.identity.signin;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletResponse;

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

    private SignInTestFixtures() {}

    public static SignInProperties properties() {
        return new SignInProperties(
                URI.create(LOGIN_ORIGIN), URI.create(ADMIN_ORIGIN), "ops@example.test", "test-keycloak-secret");
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
