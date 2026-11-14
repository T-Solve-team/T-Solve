package vn.tsolve.worker.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Duration;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

@Validated
@ConditionalOnProperty(prefix = "tsolve.worker", name = "enabled", havingValue = "true")
@ConfigurationProperties("tsolve.worker.email")
public record EmailOutboxProperties(
        @DefaultValue("30s") @NotNull Duration interval,

        @DefaultValue({"5m", "15m"}) @NotNull @Size(min = 2, max = 2)
        List<Duration> backoff,

        @DefaultValue("5m") @NotNull Duration lease,
        @DefaultValue("20") @Min(1) int batchSize,
        @DefaultValue("30d") @NotNull Duration retention) {}
