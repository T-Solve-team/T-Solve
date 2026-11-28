package vn.tsolve.platform.registry;

import java.io.PrintStream;
import java.lang.reflect.Proxy;
import java.util.Map;
import java.util.UUID;
import org.springframework.core.env.Environment;

/**
 * Plain CLI (no Spring context) used by {@code infra/scripts/registry-adopt-workspace.sh} to encrypt a Workspace
 * database password with {@link PasswordCipher} and print only the {@code v1:} value to stdout. With the argument
 * {@code verify} it compares {@code STORED_DB_PASSWORD} with {@code DB_PASSWORD} instead and prints only {@code OK}
 * (exit 0) or {@code FAIL} (exit 1); it reads no database.
 */
public final class RegistryPasswordCli {

    private static final String KEY_VAR = "CATALOG_SECRET_KEY";
    private static final String WORKSPACE_ID_VAR = "TSOLVE_DEMO_WORKSPACE_ID";
    private static final String PASSWORD_VAR = "DB_PASSWORD";
    private static final String STORED_VAR = "STORED_DB_PASSWORD";

    private RegistryPasswordCli() {}

    public static void main(String[] args) {
        int exit = run(args, System.getenv(), System.out, System.err);
        if (exit != 0) {
            System.exit(exit);
        }
    }

    public static int run(String[] args, Map<String, String> env, PrintStream out, PrintStream err) {
        if (env == null) {
            err.println("RegistryPasswordCli: environment is missing");
            return 1;
        }

        String key = env.get(KEY_VAR);
        if (key == null || key.isBlank()) {
            err.println("RegistryPasswordCli: " + KEY_VAR + " is not set (or empty)");
            return 1;
        }

        String workspaceIdRaw = env.get(WORKSPACE_ID_VAR);
        if (workspaceIdRaw == null || workspaceIdRaw.isBlank()) {
            err.println("RegistryPasswordCli: " + WORKSPACE_ID_VAR + " is not set (or empty)");
            return 1;
        }

        UUID workspaceId;
        try {
            workspaceId = UUID.fromString(workspaceIdRaw.trim());
        } catch (IllegalArgumentException exception) {
            err.println("RegistryPasswordCli: " + WORKSPACE_ID_VAR + " must be a valid UUID");
            return 1;
        }

        String password = env.get(PASSWORD_VAR);
        if (password == null || password.isBlank()) {
            err.println("RegistryPasswordCli: " + PASSWORD_VAR + " is not set (or empty)");
            return 1;
        }

        boolean verify = args.length > 0 && "verify".equals(args[0]);
        String stored = env.get(STORED_VAR);
        if (verify && (stored == null || stored.isBlank())) {
            err.println("RegistryPasswordCli: " + STORED_VAR + " is not set (or empty)");
            return 1;
        }

        try {
            Environment environment = (Environment) Proxy.newProxyInstance(
                    Environment.class.getClassLoader(), new Class<?>[] {Environment.class}, (proxy, method, params) -> {
                        if ("getProperty".equals(method.getName())
                                && params != null
                                && params.length >= 1
                                && "tsolve.registry.secret-key".equals(params[0])) {
                            return key;
                        }
                        return null;
                    });
            PasswordCipher cipher = new PasswordCipher(environment);
            if (!verify) {
                out.println(cipher.encrypt(workspaceId, password));
                return 0;
            }
            boolean matches = matches(cipher, workspaceId, stored, password);
            out.println(matches ? "OK" : "FAIL");
            return matches ? 0 : 1;
        } catch (RuntimeException exception) {
            err.println("RegistryPasswordCli: " + exception.getMessage());
            return 1;
        }
    }

    /** A wrong key or a malformed value is a mismatch; the failure itself is not reported. */
    private static boolean matches(PasswordCipher cipher, UUID workspaceId, String stored, String password) {
        try {
            return cipher.decrypt(workspaceId, stored).equals(password);
        } catch (IllegalStateException exception) {
            return false;
        }
    }
}
