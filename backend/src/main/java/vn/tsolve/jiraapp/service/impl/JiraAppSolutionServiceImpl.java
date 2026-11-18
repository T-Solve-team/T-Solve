package vn.tsolve.jiraapp.service.impl;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.RejectedExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import vn.tsolve.ai.exception.AiRewriteUnavailableException;
import vn.tsolve.common.security.JiraAppPrincipal;
import vn.tsolve.common.security.TSolveUser;
import vn.tsolve.jiraapp.dto.IssuePanelResponse;
import vn.tsolve.jiraapp.dto.JiraIssueContext.Member;
import vn.tsolve.jiraapp.dto.SolutionFormRequest;
import vn.tsolve.jiraapp.dto.SolutionFormResponse;
import vn.tsolve.jiraapp.dto.SolutionFormResponse.Origin;
import vn.tsolve.jiraapp.dto.SolutionRewriteJob;
import vn.tsolve.jiraapp.dto.SolutionRewriteJobResult;
import vn.tsolve.jiraapp.dto.SubmitSolutionRequest;
import vn.tsolve.jiraapp.dto.SubmittedTicketResponse;
import vn.tsolve.jiraapp.exception.SolutionRewriteJobNotFoundException;
import vn.tsolve.jiraapp.service.JiraAppSolutionService;
import vn.tsolve.jiraapp.service.JiraIssueAccess;
import vn.tsolve.jiraapp.service.SolutionFormAi;
import vn.tsolve.jiraapp.service.impl.RedisAskJobStore.Job;
import vn.tsolve.ticket.dto.SolutionFormSubmission;
import vn.tsolve.ticket.dto.SolutionSections;
import vn.tsolve.ticket.dto.SubmittedSolutionTicket;
import vn.tsolve.ticket.entity.TicketStatus;
import vn.tsolve.ticket.service.SolutionDraftService;
import vn.tsolve.ticket.service.SolutionFormTicketService;

// Not @Transactional: the AI calls must run outside a transaction (backend AGENTS §4); each ticket and Draft call runs
// in its own service transaction, inside the Workspace JiraIssueAccess binds.
@Service
public class JiraAppSolutionServiceImpl implements JiraAppSolutionService {

    private static final Logger log = LoggerFactory.getLogger(JiraAppSolutionServiceImpl.class);
    private static final String REWRITE_PREFIX = "jira-app:rewrite:";

    private final JiraIssueAccess access;
    private final SolutionFormTicketService tickets;
    private final SolutionDraftService drafts;
    private final SolutionFormAi ai;
    private final RedisAskJobStore jobs;

    public JiraAppSolutionServiceImpl(
            JiraIssueAccess access,
            SolutionFormTicketService tickets,
            SolutionDraftService drafts,
            SolutionFormAi ai,
            RedisAskJobStore jobs) {
        this.access = access;
        this.tickets = tickets;
        this.drafts = drafts;
        this.ai = ai;
        this.jobs = jobs;
    }

    @Override
    public SolutionFormResponse openForm(JiraAppPrincipal caller, String issueKey, SolutionFormRequest request) {
        if (request.resubmit()) {
            return openResubmission(caller, issueKey);
        }
        return access.inIssue(caller, issueKey, context -> {
            if (!context.member() || tickets.findStatus(issueKey).isPresent()) {
                return SolutionFormResponse.closed();
            }
            Member member = context.requireMember();
            Optional<SolutionSections> draft = drafts.find(member.user().id(), member.projectId(), issueKey);
            if (draft.isPresent()) {
                return new SolutionFormResponse(true, Origin.DRAFT, draft.get(), null);
            }
            return new SolutionFormResponse(
                    true, Origin.ISSUE, IssueText.sections(request.summary(), request.description()), null);
        });
    }

    // The writer's Draft first, else the ticket's current Solution; only while the ticket is resubmittable
    // (AC-012.2, AC-012.3).
    private SolutionFormResponse openResubmission(JiraAppPrincipal caller, String issueKey) {
        return access.inIssue(caller, issueKey, context -> {
            if (!context.member()) {
                return SolutionFormResponse.closed();
            }
            Member member = context.requireMember();
            Optional<SolutionSections> current =
                    tickets.findResubmittable(issueKey, member.user().id());
            if (current.isEmpty()) {
                return SolutionFormResponse.closed();
            }
            return drafts.find(member.user().id(), member.projectId(), issueKey)
                    .map(draft -> new SolutionFormResponse(true, Origin.DRAFT, draft, null))
                    .orElseGet(() -> new SolutionFormResponse(true, Origin.TICKET, current.get(), null));
        });
    }

    @Override
    public SubmittedTicketResponse submit(JiraAppPrincipal caller, String issueKey, SubmitSolutionRequest request) {
        return access.inIssue(caller, issueKey, context -> {
            Member member = context.requireMember();
            TSolveUser writer = member.user();
            SolutionFormSubmission submission = new SolutionFormSubmission(
                    member.projectId(),
                    issueKey,
                    request.summary(),
                    writer.id(),
                    writer.fullName(),
                    request.sections());
            SubmittedSolutionTicket ticket =
                    request.resubmit() ? tickets.resubmit(submission) : tickets.submit(submission);
            return new SubmittedTicketResponse(ticket.id(), ticket.ticketId(), ticket.status());
        });
    }

    @Override
    public void saveDraft(JiraAppPrincipal caller, String issueKey, SolutionSections sections) {
        access.inIssue(caller, issueKey, context -> {
            Member member = context.requireMember();
            drafts.save(member.user().id(), member.projectId(), issueKey, sections);
            return null;
        });
    }

    @Override
    public SolutionRewriteJob startRewrite(JiraAppPrincipal caller, SolutionSections sections) {
        access.asUser(caller, user -> true);
        UUID id = UUID.randomUUID();
        Job<SolutionSections> job = Job.pending(caller, false);
        try {
            jobs.create(REWRITE_PREFIX, id, job);
            ai.startRewrite(sections, (rewritten, error) -> {
                if (error != null) logFailure(id, error);
                try {
                    jobs.update(REWRITE_PREFIX, id, error == null ? job.done(rewritten) : job.failed(error));
                } catch (RuntimeException e) {
                    logFailure(id, e);
                }
            });
        } catch (DataAccessException | RejectedExecutionException e) {
            logFailure(id, e);
            try {
                jobs.delete(REWRITE_PREFIX, id);
            } catch (RuntimeException cleanupError) {
                logFailure(id, cleanupError);
            }
            throw new AiRewriteUnavailableException();
        }
        return new SolutionRewriteJob(id);
    }

    @Override
    public SolutionRewriteJobResult rewriteResult(JiraAppPrincipal caller, UUID jobId) {
        Optional<Job<SolutionSections>> stored;
        try {
            stored = jobs.find(REWRITE_PREFIX, jobId, SolutionSections.class);
        } catch (DataAccessException e) {
            logFailure(jobId, e);
            throw new AiRewriteUnavailableException();
        }
        Job<SolutionSections> job =
                stored.filter(record -> record.ownedBy(caller)).orElseThrow(SolutionRewriteJobNotFoundException::new);
        return new SolutionRewriteJobResult(job.status(), job.payload());
    }

    private static void logFailure(UUID id, Throwable error) {
        JiraAppJobs.logFailure(log, "Jira App rewrite job", id, error);
    }

    @Override
    public IssuePanelResponse panel(JiraAppPrincipal caller, String issueKey) {
        return access.inIssue(caller, issueKey, context -> {
            if (!context.linked()) {
                return IssuePanelResponse.notLinked();
            }
            UUID user = context.user().orElseThrow().id();
            Optional<TicketStatus> status = tickets.findStatus(issueKey);
            boolean hasDraft = context.member()
                    && status.isEmpty()
                    && drafts.find(user, context.projectId().orElseThrow(), issueKey)
                            .isPresent();
            boolean mayResubmit = context.member()
                    && status.filter(TicketStatus.CHANGES_REQUESTED::equals).isPresent()
                    && tickets.findResubmittable(issueKey, user).isPresent();
            return new IssuePanelResponse(
                    true, status.orElse(null), hasDraft, context.member() && status.isEmpty(), mayResubmit);
        });
    }
}
