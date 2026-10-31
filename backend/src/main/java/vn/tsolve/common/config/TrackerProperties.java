package vn.tsolve.common.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.net.URI;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** Settings of the Jira App integration (tracker module). Both come from the environment and have no default. */
@Validated
@ConfigurationProperties(prefix = "tsolve.tracker")
public record TrackerProperties(@NotBlank String jiraAppId, @NotNull URI forgeJwksUri) {}
