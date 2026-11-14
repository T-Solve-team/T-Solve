package vn.tsolve.worker.config;

import jakarta.validation.constraints.AssertTrue;
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
        @DefaultValue("30d") @NotNull Duration retention) {

    @AssertTrue(message = "tsolve.worker.email.interval must be greater than zero")
    public boolean isIntervalPositive() {
        return positive(interval);
    }

    @AssertTrue(message = "tsolve.worker.email.lease must be greater than zero")
    public boolean isLeasePositive() {
        return positive(lease);
    }

    @AssertTrue(message = "tsolve.worker.email.retention must be greater than zero")
    public boolean isRetentionPositive() {
        return positive(retention);
    }

    /**
     * A claimed batch is sent one row after another, so the lease must outlast the worst case; otherwise a slow SMTP
     * server lets the lease expire while rows are still being sent and they are counted as failed attempts.
     */
    public void requireLeaseCovers(Duration smtpConnectTimeout, Duration smtpWriteTimeout) {
        Duration worstCase = smtpConnectTimeout.plus(smtpWriteTimeout).multipliedBy(batchSize);
        if (lease.compareTo(worstCase) <= 0) {
            throw new IllegalStateException("tsolve.worker.email.lease (" + lease
                    + ") must be longer than batch-size x (SMTP connect + write timeout) = " + worstCase);
        }
    }

    private static boolean positive(Duration value) {
        return value == null || (!value.isZero() && !value.isNegative());
    }
}
