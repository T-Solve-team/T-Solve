package vn.tsolve.worker.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

@Validated
@ConditionalOnProperty(prefix = "tsolve.worker", name = "enabled", havingValue = "true")
@ConfigurationProperties("tsolve.worker")
public record WorkerProperties(
        @NotBlank String jdbcUrl,
        @NotBlank String username,
        @NotBlank String password,
        @DefaultValue("4") @Min(1) int workerCount) {}
