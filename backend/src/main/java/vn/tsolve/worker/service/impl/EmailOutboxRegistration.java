package vn.tsolve.worker.service.impl;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import vn.tsolve.worker.config.EmailOutboxProperties;
import vn.tsolve.worker.config.JobRunrRuntime;
import vn.tsolve.worker.dto.EmailOutboxDispatchRequest;

@Component
@ConditionalOnProperty(prefix = "tsolve.worker", name = "enabled", havingValue = "true")
public class EmailOutboxRegistration {
    public static final String ID = "worker-email-outbox";
    private final JobRunrRuntime runtime;
    private final EmailOutboxProperties properties;

    public EmailOutboxRegistration(JobRunrRuntime runtime, EmailOutboxProperties properties) {
        this.runtime = runtime;
        this.properties = properties;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void register() {
        if (runtime.isAutoStartup()) {
            runtime.scheduler().scheduleRecurrently(ID, properties.interval(), new EmailOutboxDispatchRequest());
        }
    }
}
