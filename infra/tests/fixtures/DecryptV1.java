import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public class DecryptV1 {
    private static final String PREFIX = "v1:";
    private static final int NONCE_BYTES = 12;
    private static final int TAG_BITS = 128;

    public static void main(String[] args) throws Exception {
        String keyB64 = System.getenv("CATALOG_SECRET_KEY");
        String wsIdStr = System.getenv("DECRYPT_WORKSPACE_ID");
        String stored = System.getenv("STORED_DB_PASSWORD");
        String expected = System.getenv("DB_PASSWORD");

        if (keyB64 == null || wsIdStr == null || stored == null || expected == null) {
            System.err.println("missing required environment variable");
            System.exit(2);
        }
        if (!stored.startsWith(PREFIX)) {
            System.err.println("missing v1: prefix");
            System.exit(1);
        }

        byte[] keyBytes = Base64.getDecoder().decode(keyB64);
        if (keyBytes.length != 32) {
            System.err.println("invalid key length");
            System.exit(1);
        }

        UUID workspaceId = UUID.fromString(wsIdStr);
        byte[] packed = Base64.getDecoder().decode(stored.substring(PREFIX.length()));
        if (packed.length < NONCE_BYTES + TAG_BITS / Byte.SIZE) {
            System.err.println("ciphertext too short");
            System.exit(1);
        }

        byte[] nonce = new byte[NONCE_BYTES];
        ByteBuffer.wrap(packed).get(nonce);

        SecretKey key = new SecretKeySpec(keyBytes, "AES");
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, nonce));
        cipher.updateAAD(ByteBuffer.allocate(16)
                .putLong(workspaceId.getMostSignificantBits())
                .putLong(workspaceId.getLeastSignificantBits())
                .array());

        String decrypted =
                new String(cipher.doFinal(packed, NONCE_BYTES, packed.length - NONCE_BYTES), StandardCharsets.UTF_8);
        if (!decrypted.equals(expected)) {
            System.err.println("decrypted value mismatch");
            System.exit(1);
        }
    }
}
