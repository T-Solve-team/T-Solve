package vn.tsolve.ticket.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Runs the ticket module's background jobs (PreviewCleanupJob). */
@Configuration
@EnableScheduling
public class TicketSchedulingConfig {}
