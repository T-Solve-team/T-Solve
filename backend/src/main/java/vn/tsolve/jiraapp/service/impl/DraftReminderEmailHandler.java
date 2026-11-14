package vn.tsolve.jiraapp.service.impl;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.jobrunr.jobs.annotations.Job;
import org.jobrunr.jobs.lambdas.JobRequestHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import vn.tsolve.common.security.TSolveUser;
import vn.tsolve.identity.account.service.AccountLookupService;
import vn.tsolve.jiraapp.client.DraftReminderMailClient;
import vn.tsolve.jiraapp.dto.DraftReminderEmailRequest;
import vn.tsolve.ticket.dto.OpenDraft;
import vn.tsolve.ticket.service.SolutionDraftService;
import vn.tsolve.worker.service.TenantExecutionService;
import vn.tsolve.worker.service.WorkerTenantService;

/**
 * Sends one writer's Draft reminder (jira-app AC-011.2, AC-011.3). A failed send fails this job only, and JobRunr
 * retries it (AC-011.4). Logs carry ids and error types, never the address or the issue keys (AC-011.7).
 */
@Component
@ConditionalOnProperty(prefix = "tsolve.worker", name = "enabled", havingValue = "true")
public class DraftReminderEmailHandler implements JobRequestHandler<DraftReminderEmailRequest> {
    private static final Logger log = LoggerFactory.getLogger(DraftReminderEmailHandler.class);

    private final WorkerTenantService tenants;
    private final TenantExecutionService execution;
    private final SolutionDraftService drafts;
    private final AccountLookupService accounts;
    private final ObjectProvider<DraftReminderMailClient> mailers;
    private final String jiraSiteUrl;

    public DraftReminderEmailHandler(
            WorkerTenantService tenants,
            TenantExecutionService execution,
            SolutionDraftService drafts,
            AccountLookupService accounts,
            ObjectProvider<DraftReminderMailClient> mailers,
            @Value("${JIRA_SITE_URL:}") String jiraSiteUrl) {
        this.tenants = tenants;
        this.execution = execution;
        this.drafts = drafts;
        this.accounts = accounts;
        this.mailers = mailers;
        this.jiraSiteUrl = jiraSiteUrl;
    }

    @Override
    @Job(name = "Jira App Draft reminder email")
    public void run(DraftReminderEmailRequest request) {
        UUID workspace = request.workspaceId();
        if (!tenants.isWorkspaceActive(workspace)) {
            return;
        }
        AtomicReference<Optional<Mail>> mail = new AtomicReference<>(Optional.empty());
        execution.runInTenant(workspace, () -> mail.set(compose(request.accountId())));
        if (mail.get().isEmpty()) {
            return;
        }
        DraftReminderMailClient sender = mailers.getIfAvailable();
        if (sender == null) {
            throw new EmailNotSentException("no mail sender is configured in this process");
        }
        Mail ready = mail.get().get();
        try {
            sender.send(
                    ready.recipient(),
                    ready.message().subject(),
                    ready.message().body());
        } catch (RuntimeException failure) {
            log.warn(
                    "Draft reminder email to account {} in Workspace {} failed with {}",
                    request.accountId(),
                    workspace,
                    failure.getClass().getName());
            // Only the type travels on: the cause's message may hold the address.
            throw new EmailNotSentException(failure.getClass().getName());
        }
        log.info(
                "Draft reminder email sent to account {} in Workspace {} ({} drafts)",
                request.accountId(),
                workspace,
                ready.drafts());
    }

    // Nothing to send for an inactive Account or a writer whose Drafts were all submitted meanwhile (AC-011.2).
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
        return Optional.of(new Mail(account.get().email(), DraftReminderMessage.of(open, jiraSiteUrl), open.size()));
    }

    private record Mail(String recipient, DraftReminderMessage message, int drafts) {}

    /** The send failed; JobRunr retries the job. Holds no address or Draft content. */
    public static final class EmailNotSentException extends RuntimeException {
        EmailNotSentException(String reason) {
            super("Draft reminder email not sent: " + reason, null, false, false);
        }
    }
}
