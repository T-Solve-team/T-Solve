package vn.tsolve.jiraapp.service.impl;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.RejectedExecutionException;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import vn.tsolve.common.security.JiraAppPrincipal;
import vn.tsolve.common.security.TSolveUser;
import vn.tsolve.identity.service.ScopeAccessService;
import vn.tsolve.identity.signin.port.TenantScope;
import vn.tsolve.jiraapp.client.JiraStatusClient;
import vn.tsolve.jiraapp.dto.IssueUpdatedPayload;
import vn.tsolve.jiraapp.dto.IssueUpdatedPayload.Item;
import vn.tsolve.jiraapp.service.IssueUpdatedService;
import vn.tsolve.jiraapp.service.JiraAskerLookup;
import vn.tsolve.jiraapp.service.JiraWorkspaceLookup;
import vn.tsolve.jiraapp.service.SolutionFormAi;
import vn.tsolve.project.service.ProjectService;
import vn.tsolve.ticket.dto.IssueStatusChange;
import vn.tsolve.ticket.dto.SolutionSections;
import vn.tsolve.ticket.service.SolutionDraftService;
import vn.tsolve.ticket.service.SolutionFormTicketService;
import vn.tsolve.tracker.service.JiraServiceAccount;
import vn.tsolve.tracker.service.TrackerIssueService;

/**
 * Reads run inside the site's Workspace first, then — only for an issue T-Solve has not recorded yet — Jira is asked
 * for the category of the status before the change, outside any transaction; then the Done state and the Draft are
 * written together (AC-005.10). The event's {@code from} is a status id, not a category, so the recorded state is the
 * "before" whenever there is one (design, KAN-371).
 */
@Service
public class IssueUpdatedServiceImpl implements IssueUpdatedService {

    private static final Logger log = LoggerFactory.getLogger(IssueUpdatedServiceImpl.class);
    private static final Pattern ISSUE_KEY = Pattern.compile("^[A-Z][A-Z0-9_]*-[1-9][0-9]*$");
    private static final int MAX_KEY_LENGTH = 50;

    private final JiraWorkspaceLookup workspaces;
    private final TenantScope tenantScope;
    private final ProjectService projects;
    private final JiraAskerLookup users;
    private final ScopeAccessService scopes;
    private final SolutionFormTicketService tickets;
    private final SolutionDraftService drafts;
    private final JiraServiceAccount jiraAccounts;
    private final JiraStatusClient statuses;
    private final TrackerIssueService issues;
    private final SolutionFormAi formAi;

    public IssueUpdatedServiceImpl(
            JiraWorkspaceLookup workspaces,
            TenantScope tenantScope,
            ProjectService projects,
            JiraAskerLookup users,
            ScopeAccessService scopes,
            SolutionFormTicketService tickets,
            SolutionDraftService drafts,
            JiraServiceAccount jiraAccounts,
            JiraStatusClient statuses,
            TrackerIssueService issues,
            SolutionFormAi formAi) {
        this.workspaces = workspaces;
        this.tenantScope = tenantScope;
        this.projects = projects;
        this.users = users;
        this.scopes = scopes;
        this.tickets = tickets;
        this.drafts = drafts;
        this.jiraAccounts = jiraAccounts;
        this.statuses = statuses;
        this.issues = issues;
        this.formAi = formAi;
    }

    @Override
    public void receive(JiraAppPrincipal caller, IssueUpdatedPayload event) {
        Optional<String> key = event.issueKey()
                .filter(value -> value.length() <= MAX_KEY_LENGTH
                        && ISSUE_KEY.matcher(value).matches());
        Optional<Item> change = event.statusChange();
        Optional<String> category = event.statusCategory();
        if (key.isEmpty() || change.isEmpty() || category.isEmpty()) {
            return;
        }
        Optional<UUID> workspace = workspaces.workspaceOf(caller == null ? null : caller.cloudId());
        if (workspace.isEmpty()) {
            log.info("Issue update from a site with no Workspace: {}", key.get());
            return;
        }
        String issueKey = key.get();
        boolean done = IssueUpdatedPayload.DONE.equals(category.get());
        Optional<Before> before = tenantScope.runIn(workspace.get(), () -> projects.findActivePublicIdByJiraProjectKey(
                        JiraIssueAccessImpl.projectKey(issueKey))
                .map(project -> new Before(project, drafts.findIssueDone(project, issueKey))));
        if (before.isEmpty()) {
            return;
        }
        UUID project = before.get().project();
        boolean movedIntoDone = done
                && !wasDone(
                        workspace.get(), before.get().recorded(), change.get().from());
        String description = AdfText.issueText(event.description());
        Optional<UUID> created = tenantScope.runIn(workspace.get(), () -> {
            Optional<UUID> owner = movedIntoDone ? owner(caller, event, project, issueKey) : Optional.empty();
            SolutionSections sections = owner.isPresent() ? IssueText.sections(event.summary(), description) : null;
            boolean isCreated = drafts.recordIssueStatus(
                    new IssueStatusChange(project, issueKey, done, owner.orElse(null), sections));
            return isCreated ? owner : Optional.<UUID>empty();
        });
        log.info("Issue status recorded: {} done={} draftOffered={}", issueKey, done, movedIntoDone);
        created.ifPresent(
                owner -> startAiDraft(workspace.get(), project, issueKey, owner, event.summary(), description));
    }

    // AC-005.11: in the background, so the event is still answered at once; any failure keeps the Draft (AC-005.12).
    private void startAiDraft(
            UUID workspace, UUID project, String issueKey, UUID owner, String summary, String description) {
        try {
            formAi.startDraft(
                    summary,
                    description,
                    () -> tenantScope.runIn(workspace, () -> issues.comments(issueKey)),
                    sections -> {
                        boolean isReplaced = tenantScope.runIn(
                                workspace, () -> drafts.replaceUnsavedDraft(owner, project, issueKey, sections));
                        log.info("Issue AI draft {}: {}", isReplaced ? "applied" : "dropped, Draft saved", issueKey);
                    });
        } catch (RejectedExecutionException e) {
            log.info("Issue AI draft not started, pool full: {}", issueKey);
        }
    }

    // A failed or unknown read counts as "not Done before": a missing Draft costs a reminder, an extra one nothing.
    private boolean wasDone(UUID workspace, Optional<Boolean> recorded, String fromStatusId) {
        if (recorded.isPresent()) {
            return recorded.get();
        }
        return tenantScope
                .runIn(workspace, jiraAccounts::current)
                .flatMap(account -> statuses.categoryOf(account, fromStatusId))
                .map(IssueUpdatedPayload.DONE::equals)
                .orElse(false);
    }

    // The assignee, else the mover: linked, a member of the Project, and the issue has no ticket (AC-005.8).
    private Optional<UUID> owner(JiraAppPrincipal caller, IssueUpdatedPayload event, UUID project, String issueKey) {
        if (tickets.findStatus(issueKey).isPresent()) {
            return Optional.empty();
        }
        return event.draftOwner()
                .flatMap(accountId -> users.find(caller.cloudId(), accountId))
                .map(TSolveUser::id)
                .filter(user -> scopes.canAccessProject(user, project));
    }

    private record Before(UUID project, Optional<Boolean> recorded) {}
}
