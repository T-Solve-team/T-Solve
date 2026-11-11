package vn.tsolve.jiraapp.service.impl;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import vn.tsolve.common.security.JiraAppPrincipal;
import vn.tsolve.common.security.TSolveUser;
import vn.tsolve.identity.service.ScopeAccessService;
import vn.tsolve.jiraapp.dto.IssuePanelResponse;
import vn.tsolve.jiraapp.dto.JiraIssueContext;
import vn.tsolve.jiraapp.dto.JiraIssueContext.Member;
import vn.tsolve.jiraapp.dto.SolutionFormDraft;
import vn.tsolve.jiraapp.dto.SolutionFormRequest;
import vn.tsolve.jiraapp.dto.SolutionFormResponse;
import vn.tsolve.jiraapp.dto.SolutionFormResponse.Origin;
import vn.tsolve.jiraapp.dto.SubmitSolutionRequest;
import vn.tsolve.jiraapp.dto.SubmittedTicketResponse;
import vn.tsolve.jiraapp.repository.JiraProjectDepartmentRepository;
import vn.tsolve.jiraapp.service.JiraAppSolutionService;
import vn.tsolve.jiraapp.service.JiraIssueAccess;
import vn.tsolve.jiraapp.service.SolutionFormAi;
import vn.tsolve.ticket.dto.MessageRef;
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

    private static final MessageRef AI_DRAFT_UNAVAILABLE = new MessageRef("MSG67", Map.of());

    private final JiraIssueAccess access;
    private final SolutionFormTicketService tickets;
    private final SolutionDraftService drafts;
    private final SolutionFormAi ai;
    private final ScopeAccessService scopes;
    private final JiraProjectDepartmentRepository departments;

    public JiraAppSolutionServiceImpl(
            JiraIssueAccess access,
            SolutionFormTicketService tickets,
            SolutionDraftService drafts,
            SolutionFormAi ai,
            ScopeAccessService scopes,
            JiraProjectDepartmentRepository departments) {
        this.access = access;
        this.tickets = tickets;
        this.drafts = drafts;
        this.ai = ai;
        this.scopes = scopes;
        this.departments = departments;
    }

    @Override
    public SolutionFormResponse openForm(JiraAppPrincipal caller, String issueKey, SolutionFormRequest request) {
        return access.inIssue(caller, issueKey, context -> {
            if (!context.member() || tickets.findStatus(issueKey).isPresent()) {
                return SolutionFormResponse.closed();
            }
            Member member = context.requireMember();
            Optional<SolutionSections> draft = drafts.find(member.user().id(), member.projectId(), issueKey);
            if (draft.isPresent()) {
                return new SolutionFormResponse(true, Origin.DRAFT, draft.get(), null);
            }
            SolutionFormDraft aiDraft = ai.draft(request.summary(), request.description());
            return aiDraft.aiDraft()
                    ? new SolutionFormResponse(true, Origin.AI_DRAFT, aiDraft.sections(), null)
                    : new SolutionFormResponse(true, Origin.ISSUE, aiDraft.sections(), AI_DRAFT_UNAVAILABLE);
        });
    }

    @Override
    public SubmittedTicketResponse submit(JiraAppPrincipal caller, String issueKey, SubmitSolutionRequest request) {
        return access.inIssue(caller, issueKey, context -> {
            Member member = context.requireMember();
            TSolveUser writer = member.user();
            SubmittedSolutionTicket ticket = tickets.submit(new SolutionFormSubmission(
                    member.projectId(),
                    issueKey,
                    request.summary(),
                    writer.id(),
                    writer.fullName(),
                    request.sections()));
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
    public SolutionSections rewrite(JiraAppPrincipal caller, SolutionSections sections) {
        return access.asUser(caller, user -> ai.rewrite(sections));
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
            return new IssuePanelResponse(
                    true,
                    status.orElse(null),
                    hasDraft,
                    context.member() && status.isEmpty(),
                    canSearch(context, user));
        });
    }

    // MSG76 when the user may see no Department of the issue's Project (AC-008.6).
    private boolean canSearch(JiraIssueContext context, UUID user) {
        return context.projectId()
                .flatMap(departments::departmentOf)
                .map(department -> scopes.canAccessDepartment(user, department))
                .orElse(false);
    }
}
