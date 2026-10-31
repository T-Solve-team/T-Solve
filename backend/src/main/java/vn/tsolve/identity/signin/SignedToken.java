package vn.tsolve.identity.signin;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.Optional;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

/**
 * Signs a payload as {@code payload.mac} with HMAC-SHA256 and the server's {@code TSOLVE_SIGNING_KEY}, and checks
 * such a value. Only the server holds the key, so a value it accepts was made by it.
 */
@Component
public class SignedToken {

    private static final String ALGORITHM = "HmacSHA256";

    private final SecretKeySpec key;

    public SignedToken(SignInProperties properties) {
        this.key = new SecretKeySpec(Base64.getDecoder().decode(properties.signingKey()), ALGORITHM);
    }

    public String sign(String payload) {
        return payload + "." + mac(payload);
    }

    /** The payload when the value is well formed and signed with this key; empty otherwise. Never throws. */
    public Optional<String> verify(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        int dot = token.indexOf('.');
        if (dot <= 0 || dot != token.lastIndexOf('.') || dot == token.length() - 1) {
            return Optional.empty();
        }
        String payload = token.substring(0, dot);
        String presented = token.substring(dot + 1);
        // the canonical text of the MAC is compared, so a MAC spelled differently is not accepted either
        boolean valid = MessageDigest.isEqual(
                mac(payload).getBytes(StandardCharsets.US_ASCII), presented.getBytes(StandardCharsets.US_ASCII));
        return valid ? Optional.of(payload) : Optional.empty();
    }

    private String mac(String payload) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(key);
            byte[] digest = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HMAC-SHA256 is not available", e);
        }
    }
}
