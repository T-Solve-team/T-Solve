package vn.tsolve.platform.registry;

import java.io.PrintStream;
import java.lang.reflect.Proxy;
import java.util.Map;
import java.util.UUID;
import org.springframework.core.env.Environment;

/**
 * Plain CLI (no Spring context) used by {@code infra/scripts/registry-adopt-workspace.sh} to encrypt a Workspace
 * database password with {@link PasswordCipher} and print only the {@code v1:} value to stdout.
 */
public final class RegistryPasswordCli {

    private static final String KEY_VAR = "CATALOG_SECRET_KEY";
    private static final String WORKSPACE_ID_VAR = "TSOLVE_DEMO_WORKSPACE_ID";
    private static final String PASSWORD_VAR = "DB_PASSWORD";

    private RegistryPasswordCli() {}

    public static void main(String[] args) {
        int exit = run(System.getenv(), System.out, System.err);
        if (exit != 0) {
            System.exit(exit);
        }
    }

    public static int run(Map<String, String> env, PrintStream out, PrintStream err) {
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

        try {
            Environment environment = (Environment) Proxy.newProxyInstance(
                    Environment.class.getClassLoader(), new Class<?>[] {Environment.class}, (proxy, method, args) -> {
                        if ("getProperty".equals(method.getName())
                                && args != null
                                && args.length >= 1
                                && "tsolve.registry.secret-key".equals(args[0])) {
                            return key;
                        }
                        return null;
                    });
            PasswordCipher cipher = new PasswordCipher(environment);
            out.println(cipher.encrypt(workspaceId, password));
            return 0;
        } catch (RuntimeException exception) {
            err.println("RegistryPasswordCli: " + exception.getMessage());
            return 1;
        }
    }
}
