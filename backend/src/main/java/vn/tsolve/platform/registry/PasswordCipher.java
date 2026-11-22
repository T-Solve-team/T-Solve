package vn.tsolve.platform.registry;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.UUID;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.core.env.Environment;

/**
 * Encrypts the Workspace database password kept in the Registry. Same format as the workspace module's TokenCipher
 * (copied, because platform must not depend on workspace) with its own key, CATALOG_SECRET_KEY.
 */
public class PasswordCipher {

    private static final String PREFIX = "v1:";
    private static final int NONCE_BYTES = 12;
    private static final int TAG_BITS = 128;

    private final SecretKey key;
    private final SecureRandom random = new SecureRandom();

    public PasswordCipher(Environment environment) {
        // Read directly rather than through a binding error that could echo a rejected secret value.
        try {
            String encoded = environment.getProperty("tsolve.registry.secret-key");
            byte[] decoded = Base64.getDecoder().decode(encoded);
            if (decoded.length != 32) {
                throw new IllegalArgumentException();
            }
            key = new SecretKeySpec(decoded, "AES");
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw new IllegalStateException("CATALOG_SECRET_KEY must be Base64 encoding of exactly 32 bytes");
        }
    }

    public String encrypt(UUID workspaceId, String password) {
        try {
            byte[] nonce = new byte[NONCE_BYTES];
            random.nextBytes(nonce);
            Cipher cipher = configuredCipher(Cipher.ENCRYPT_MODE, workspaceId, nonce);
            byte[] ciphertext = cipher.doFinal(password.getBytes(StandardCharsets.UTF_8));
            byte[] packed = ByteBuffer.allocate(nonce.length + ciphertext.length)
                    .put(nonce)
                    .put(ciphertext)
                    .array();
            return PREFIX + Base64.getEncoder().encodeToString(packed);
        } catch (GeneralSecurityException | IllegalArgumentException | NullPointerException exception) {
            throw new IllegalStateException("Workspace database password encryption failed");
        }
    }

    public String decrypt(UUID workspaceId, String stored) {
        try {
            if (!stored.startsWith(PREFIX)) {
                throw new IllegalArgumentException();
            }
            byte[] packed = Base64.getDecoder().decode(stored.substring(PREFIX.length()));
            if (packed.length < NONCE_BYTES + TAG_BITS / Byte.SIZE) {
                throw new IllegalArgumentException();
            }
            byte[] nonce = new byte[NONCE_BYTES];
            ByteBuffer.wrap(packed).get(nonce);
            Cipher cipher = configuredCipher(Cipher.DECRYPT_MODE, workspaceId, nonce);
            return new String(cipher.doFinal(packed, NONCE_BYTES, packed.length - NONCE_BYTES), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException | NullPointerException exception) {
            // Decoder and provider causes are deliberately omitted: callers can log this exception safely.
            throw new IllegalStateException("Workspace database password decryption failed");
        }
    }

    private Cipher configuredCipher(int mode, UUID workspaceId, byte[] nonce) throws GeneralSecurityException {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(mode, key, new GCMParameterSpec(TAG_BITS, nonce));
        cipher.updateAAD(ByteBuffer.allocate(16)
                .putLong(workspaceId.getMostSignificantBits())
                .putLong(workspaceId.getLeastSignificantBits())
                .array());
        return cipher;
    }
}
