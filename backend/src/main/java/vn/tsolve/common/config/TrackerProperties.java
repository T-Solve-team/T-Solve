package vn.tsolve.common.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Settings of the Jira App integration (tracker module). The app id, JWKS URI and token key come from the
 * environment and have no default; the worker timings are the design's fixed values (design, Key Decisions).
 */
@Validated
@ConfigurationProperties(prefix = "tsolve.tracker")
public record TrackerProperties(
        @NotBlank String jiraAppId,
        @NotNull URI forgeJwksUri,
        @NotBlank String tokenKey,
        @Valid @DefaultValue Worker worker) {

    /**
     * @param pollInterval delay between two worker runs
     * @param staleClaimAfter a PROCESSING event claimed longer ago returns to RECEIVED (worker crashed)
     * @param retryDelays delay before each retry; when they run out the event is FAILED
     * @param retention finished events older than this are deleted
     */
    public record Worker(
            @DefaultValue("5s") @NotNull Duration pollInterval,
            @DefaultValue("10m") @NotNull Duration staleClaimAfter,
            @DefaultValue({"1m", "5m", "15m"}) @NotEmpty List<Duration> retryDelays,
            @DefaultValue("30d") @NotNull Duration retention) {}

    // The record's own toString would print the token key into logs.
    @Override
    public String toString() {
        return "TrackerProperties[jiraAppId=" + jiraAppId + ", forgeJwksUri=" + forgeJwksUri + ", tokenKey=***, worker="
                + worker + "]";
    }
}
