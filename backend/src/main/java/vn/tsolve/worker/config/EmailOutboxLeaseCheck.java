package vn.tsolve.worker.config;

import jakarta.annotation.PostConstruct;
import java.time.Duration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/** Fails startup when the outbox lease is shorter than a full batch of slow SMTP sends. */
@Component
@ConditionalOnProperty(prefix = "tsolve.worker", name = "enabled", havingValue = "true")
public class EmailOutboxLeaseCheck {
    private static final String SMTP = "spring.mail.properties[mail.smtp.";
    private static final long DEFAULT_CONNECT_MS = 5000;
    private static final long DEFAULT_WRITE_MS = 5000;

    private final EmailOutboxProperties properties;
    private final Environment environment;

    public EmailOutboxLeaseCheck(EmailOutboxProperties properties, Environment environment) {
        this.properties = properties;
        this.environment = environment;
    }

    @PostConstruct
    void check() {
        properties.requireLeaseCovers(
                millis("connectiontimeout", DEFAULT_CONNECT_MS), millis("writetimeout", DEFAULT_WRITE_MS));
    }

    private Duration millis(String key, long fallback) {
        return Duration.ofMillis(environment.getProperty(SMTP + key + "]", Long.class, fallback));
    }
}
