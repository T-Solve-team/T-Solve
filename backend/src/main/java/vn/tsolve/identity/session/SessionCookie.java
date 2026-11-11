package vn.tsolve.identity.session;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

/**
 * The {@code __Host-TSOLVE_SESSION} cookie: Secure, HttpOnly, {@code SameSite=Lax}, {@code Path=/}, no
 * {@code Domain} (so only the one site gets it) and no {@code Max-Age} (it ends with the browser session; the server
 * enforces the real limits, AC-001.23).
 */
@Component
public class SessionCookie {

    public static final String NAME = "__Host-TSOLVE_SESSION";

    private static final int RANDOM_BYTES = 32;

    private final SecureRandom random = new SecureRandom();

    /** 32 random bytes as 43 base64url characters. */
    public String newValue() {
        byte[] bytes = new byte[RANDOM_BYTES];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /** The cookie value as sent, or null. */
    public String valueOf(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie candidate : cookies) {
                if (NAME.equals(candidate.getName())) {
                    return candidate.getValue();
                }
            }
        }
        return null;
    }

    public ResponseCookie issue(String value) {
        return base(value).build();
    }

    public ResponseCookie expire() {
        return base("").maxAge(Duration.ZERO).build();
    }

    private static ResponseCookie.ResponseCookieBuilder base(String value) {
        return ResponseCookie.from(NAME, value)
                .httpOnly(true)
                .secure(true)
                .sameSite("Lax")
                .path("/");
    }
}
