package vn.tsolve.identity.signin.port;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.util.StringUtils;

/**
 * Which implementation of {@link WorkspaceSignInProvisioner} is wired (AC-001.60): the Keycloak adapter in the {@code
 * ops} profile with the three secrets set, the no-op bean in every other case. Kept apart from both implementations,
 * so the port does not depend on its adapter.
 */
public final class ProvisioningWiring {

    private static final String PREFIX = "tsolve.keycloak-provisioning";

    private ProvisioningWiring() {}

    /** True in the {@code ops} profile with all three values set: the Keycloak adapter is wired. */
    public static final class Configured implements Condition {
        @Override
        public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
            return isConfigured(context.getEnvironment());
        }
    }

    /** The opposite: the no-op bean is wired. */
    public static final class NotConfigured implements Condition {
        @Override
        public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
            return !isConfigured(context.getEnvironment());
        }
    }

    private static boolean isConfigured(Environment env) {
        return env.acceptsProfiles(Profiles.of("ops"))
                && StringUtils.hasText(env.getProperty(PREFIX + ".admin-client-secret"))
                && StringUtils.hasText(env.getProperty(PREFIX + ".google-client-id"))
                && StringUtils.hasText(env.getProperty(PREFIX + ".google-client-secret"));
    }
}
