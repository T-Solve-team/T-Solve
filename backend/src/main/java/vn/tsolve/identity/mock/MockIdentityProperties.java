package vn.tsolve.identity.mock;

import java.util.UUID;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Profile;

/** Interim mock — replace when KAN-275 workspace-session (identity Tasks 26a/26b) lands; never enable in prod. */
@Profile("identity-mock")
@ConfigurationProperties("tsolve.identity.mock")
public record MockIdentityProperties(String accountEmail, UUID workspaceId) {}
