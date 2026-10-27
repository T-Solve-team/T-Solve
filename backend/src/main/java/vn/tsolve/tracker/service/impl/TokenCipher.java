package vn.tsolve.tracker.service.impl;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;
import vn.tsolve.common.config.TrackerProperties;
import vn.tsolve.tracker.exception.TokenUnreadableException;

/**
 * Encrypts the Jira system token kept with a RECEIVED event (Non-Functional 3): AES-256-GCM with a random 12-byte
 * IV, stored as {@code iv || ciphertext || tag}. The key is {@code TSOLVE_TOKEN_KEY}, 32 bytes in base64; the
 * application refuses to start without a valid one (edge case 20).
 */
@Component
public class TokenCipher {

    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int KEY_BYTES = 32;
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;

    private final SecretKey key;
    private final SecureRandom random = new SecureRandom();

    public TokenCipher(TrackerProperties properties) {
        this.key = decodeKey(properties.tokenKey());
    }

    public byte[] encrypt(String token) {
        byte[] iv = new byte[IV_BYTES];
        random.nextBytes(iv);
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            byte[] sealed = cipher.doFinal(token.getBytes(StandardCharsets.UTF_8));
            return ByteBuffer.allocate(IV_BYTES + sealed.length)
                    .put(iv)
                    .put(sealed)
                    .array();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("AES-GCM unavailable", e);
        }
    }

    /** @throws TokenUnreadableException when the key changed or the bytes were damaged */
    public String decrypt(byte[] stored) {
        if (stored == null || stored.length <= IV_BYTES) {
            throw new TokenUnreadableException(null);
        }
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, stored, 0, IV_BYTES));
            byte[] plain = cipher.doFinal(stored, IV_BYTES, stored.length - IV_BYTES);
            return new String(plain, StandardCharsets.UTF_8);
        } catch (GeneralSecurityException e) {
            throw new TokenUnreadableException(e);
        }
    }

    // The message names the setting, never its value.
    private static SecretKey decodeKey(String base64) {
        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(base64.trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("TSOLVE_TOKEN_KEY is not valid base64");
        }
        if (bytes.length != KEY_BYTES) {
            throw new IllegalStateException("TSOLVE_TOKEN_KEY must decode to 32 bytes");
        }
        return new SecretKeySpec(bytes, "AES");
    }
}
