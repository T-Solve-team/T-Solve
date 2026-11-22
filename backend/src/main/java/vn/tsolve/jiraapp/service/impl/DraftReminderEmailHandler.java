package vn.tsolve.jiraapp.service.impl;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.jobrunr.jobs.annotations.Job;
import org.jobrunr.jobs.lambdas.JobRequestHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import vn.tsolve.common.security.TSolveUser;
import vn.tsolve.identity.account.service.AccountLookupService;
import vn.tsolve.jiraapp.dto.DraftReminderEmailRequest;
import vn.tsolve.ticket.dto.OpenDraft;
import vn.tsolve.ticket.service.SolutionDraftService;
import vn.tsolve.worker.dto.OutboundEmail;
import vn.tsolve.worker.exception.InvalidOutboundEmailException;
import vn.tsolve.worker.service.EmailOutboxService;
import vn.tsolve.worker.service.TenantExecutionService;
import vn.tsolve.worker.service.WorkerTenantService;

/**
 * Queues one writer's Draft reminder in the shared email outbox (jira-app AC-011.2 to AC-011.4, AC-011.6). The outbox
 * sends and retries delivery; a failed queue call fails this job only, and JobRunr retries it. Logs carry ids, counts
 * and error types, never the address or the issue keys (AC-011.7).
 */
@Component
@ConditionalOnProperty(prefix = "tsolve.worker", name = "enabled", havingValue = "true")
public class DraftReminderEmailHandler implements JobRequestHandler<DraftReminderEmailRequest> {
    private static final Logger log = LoggerFactory.getLogger(DraftReminderEmailHandler.class);

    private final WorkerTenantService tenants;
    private final TenantExecutionService execution;
    private final SolutionDraftService drafts;
    private final AccountLookupService accounts;
    private final EmailOutboxService outbox;
    private final String jiraSiteUrl;

    public DraftReminderEmailHandler(
            WorkerTenantService tenants,
            TenantExecutionService execution,
            SolutionDraftService drafts,
            AccountLookupService accounts,
            EmailOutboxService outbox,
            @Value("${JIRA_SITE_URL:}") String jiraSiteUrl) {
        this.tenants = tenants;
        this.execution = execution;
        this.drafts = drafts;
        this.accounts = accounts;
        this.outbox = outbox;
        this.jiraSiteUrl = jiraSiteUrl;
    }

    @Override
    @Job(name = "Jira App Draft reminder email")
    public void run(DraftReminderEmailRequest request) {
        UUID workspace = request.workspaceId();
        if (!tenants.isWorkspaceActive(workspace)) {
            return;
        }
        AtomicInteger queued = new AtomicInteger();
        try {
            // The outbox stores the Workspace of the bound tenant, so the queue call runs inside it.
            execution.runInTenant(workspace, () -> compose(request.accountId()).ifPresent(mail -> {
                outbox.queue(mail.email());
                queued.set(mail.drafts());
            }));
        } catch (InvalidOutboundEmailException invalid) {
            // The Account's address is not one mailbox: every retry would fail the same way.
            log.warn(
                    "Draft reminder for account {} in Workspace {} not queued: invalid email",
                    request.accountId(),
                    workspace);
            return;
        } catch (RuntimeException failure) {
            log.warn(
                    "Draft reminder for account {} in Workspace {} not queued ({})",
                    request.accountId(),
                    workspace,
                    failure.getClass().getSimpleName());
            // Only the type travels on: the cause's message may hold the address. JobRunr retries this job.
            throw new EmailNotQueuedException(failure.getClass().getSimpleName());
        }
        if (queued.get() > 0) {
            log.info(
                    "Draft reminder queued for account {} in Workspace {} ({} drafts)",
                    request.accountId(),
                    workspace,
                    queued.get());
        }
    }

    // Nothing to queue for an inactive Account or a writer whose Drafts were all submitted meanwhile (AC-011.2).
    private Optional<Mail> compose(UUID accountId) {
        Optional<TSolveUser> account = accounts.findActive(accountId);
        if (account.isEmpty()
                || account.get().email() == null
                || account.get().email().isBlank()) {
            return Optional.empty();
        }
        List<OpenDraft> open = drafts.findOpenDrafts(accountId);
        if (open.isEmpty()) {
            return Optional.empty();
        }
        DraftReminderMessage message =
                DraftReminderMessage.of(open, jiraSiteUrl, account.get().fullName());
        return Optional.of(new Mail(
                new OutboundEmail(account.get().email(), message.subject(), message.body(), message.html()),
                open.size()));
    }

    private record Mail(OutboundEmail email, int drafts) {}

    /** The queue call failed; JobRunr retries the job (AC-011.4). Holds no address or Draft content. */
    public static final class EmailNotQueuedException extends RuntimeException {
        EmailNotQueuedException(String reason) {
            super("Draft reminder email not queued: " + reason, null, false, false);
        }
    }
}
