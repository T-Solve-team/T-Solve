package vn.tsolve.identity.signin;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

/**
 * The browser-binding cookie of one sign-in attempt (AC-001.44). {@code __Host-} means Secure, {@code Path=/} and no
 * {@code Domain}, so only {@code login.} itself can set or read it. The server keeps only the SHA-256 of the value.
 */
@Component
public class SignInCookie {

    static final String NAME = "__Host-TSOLVE_SIGNIN";
    static final Duration MAX_AGE = Duration.ofMinutes(10);

    private static final int RANDOM_BYTES = 32;

    private final SecureRandom random = new SecureRandom();

    /** 32 random bytes as 43 base64url characters. */
    String newValue() {
        byte[] bytes = new byte[RANDOM_BYTES];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /** SHA-256 of the value as sent (its US-ASCII bytes), so a malformed value simply fails to match. */
    byte[] hash(String value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.US_ASCII));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }

    boolean matches(String presentedValue, byte[] storedHash) {
        if (presentedValue == null || presentedValue.isEmpty() || storedHash == null) {
            return false;
        }
        // constant-time compare
        return MessageDigest.isEqual(hash(presentedValue), storedHash);
    }

    ResponseCookie issue(String value) {
        return base(value).maxAge(MAX_AGE).build();
    }

    ResponseCookie expire() {
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
