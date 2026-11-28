package vn.tsolve.jiraapp.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.Consumer;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import vn.tsolve.common.security.JiraAppPrincipal;
import vn.tsolve.common.security.TSolveUser;
import vn.tsolve.identity.service.ScopeAccessService;
import vn.tsolve.identity.signin.port.TenantScope;
import vn.tsolve.jiraapp.client.JiraStatusClient;
import vn.tsolve.jiraapp.dto.IssueUpdatedPayload;
import vn.tsolve.jiraapp.dto.IssueUpdatedPayload.Assignee;
import vn.tsolve.jiraapp.dto.IssueUpdatedPayload.Changelog;
import vn.tsolve.jiraapp.dto.IssueUpdatedPayload.Fields;
import vn.tsolve.jiraapp.dto.IssueUpdatedPayload.Issue;
import vn.tsolve.jiraapp.dto.IssueUpdatedPayload.Item;
import vn.tsolve.jiraapp.dto.IssueUpdatedPayload.Status;
import vn.tsolve.jiraapp.dto.IssueUpdatedPayload.StatusCategory;
import vn.tsolve.jiraapp.service.JiraAskerLookup;
import vn.tsolve.jiraapp.service.JiraWorkspaceLookup;
import vn.tsolve.jiraapp.service.SolutionFormAi;
import vn.tsolve.project.service.ProjectService;
import vn.tsolve.ticket.dto.IssueStatusChange;
import vn.tsolve.ticket.dto.SolutionSections;
import vn.tsolve.ticket.entity.TicketStatus;
import vn.tsolve.ticket.service.SolutionDraftService;
import vn.tsolve.ticket.service.SolutionFormTicketService;
import vn.tsolve.tracker.dto.JiraAccount;
import vn.tsolve.tracker.service.JiraServiceAccount;
import vn.tsolve.tracker.service.TrackerIssueService;

class IssueUpdatedServiceImplTest {

    private static final String ISSUE = "ITSUP-7";
    private static final UUID WORKSPACE = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
    private static final UUID PROJECT = UUID.fromString("00000000-0000-0000-0000-000000000126");
    private static final TSolveUser ASSIGNEE =
            new TSolveUser(UUID.fromString("00000000-0000-0000-0000-000000000001"), "a@x.test", "An", Set.of());
    private static final TSolveUser MOVER =
            new TSolveUser(UUID.fromString("00000000-0000-0000-0000-000000000002"), "m@x.test", "Minh", Set.of());
    private static final JiraAppPrincipal SITE = new JiraAppPrincipal("cloud-1", null, null);
    private static final JiraAccount APP = new JiraAccount("https://x.atlassian.net", "cloud-1", "token");
    private static final SolutionSections ISSUE_TEXT =
            new SolutionSections("VPN drops\n\nReconnect fails.", "", "", "", "");

    private final JiraWorkspaceLookup workspaces = mock(JiraWorkspaceLookup.class);
    private final ProjectService projects = mock(ProjectService.class);
    private final JiraAskerLookup users = mock(JiraAskerLookup.class);
    private final ScopeAccessService scopes = mock(ScopeAccessService.class);
    private final SolutionFormTicketService tickets = mock(SolutionFormTicketService.class);
    private final SolutionDraftService drafts = mock(SolutionDraftService.class);
    private final JiraServiceAccount jiraAccounts = mock(JiraServiceAccount.class);
    private final JiraStatusClient statuses = mock(JiraStatusClient.class);
    private final TrackerIssueService issues = mock(TrackerIssueService.class);
    private final SolutionFormAi formAi = mock(SolutionFormAi.class);
    private final TenantScope tenantScope = new TenantScope() {
        @Override
        public UUID currentWorkspaceId() {
            return WORKSPACE;
        }

        @Override
        public <T> T runIn(UUID workspaceId, Supplier<T> work) {
            assertThat(workspaceId).isEqualTo(WORKSPACE);
            return work.get();
        }
    };
    private final IssueUpdatedServiceImpl service = new IssueUpdatedServiceImpl(
            workspaces, tenantScope, projects, users, scopes, tickets, drafts, jiraAccounts, statuses, issues, formAi);

    @BeforeEach
    void aConnectedSiteAndProject() {
        given(workspaces.workspaceOf("cloud-1")).willReturn(Optional.of(WORKSPACE));
        given(projects.findActivePublicIdByJiraProjectKey("ITSUP")).willReturn(Optional.of(PROJECT));
        given(drafts.findIssueDone(PROJECT, ISSUE)).willReturn(Optional.of(false));
        given(users.find("cloud-1", "jira-assignee")).willReturn(Optional.of(ASSIGNEE));
        given(users.find("cloud-1", "jira-mover")).willReturn(Optional.of(MOVER));
        given(scopes.canAccessProject(any(), any())).willReturn(true);
        given(tickets.findStatus(ISSUE)).willReturn(Optional.empty());
    }

    @Test
    void createsTheAssigneesDraftWithTheIssueTextOnAMoveIntoDone_AC_005_7() throws Exception {
        service.receive(SITE, event("done", "jira-assignee"));

        assertThat(recorded()).isEqualTo(new IssueStatusChange(PROJECT, ISSUE, true, ASSIGNEE.id(), ISSUE_TEXT));
    }

    @Test
    @SuppressWarnings("unchecked")
    void startsTheAiDraftOfACreatedDraftAndReplacesItWhileUnsaved_AC_005_11() throws Exception {
        given(drafts.recordIssueStatus(any())).willReturn(true);
        given(issues.comments(ISSUE)).willReturn(List.of("Restarted"));

        service.receive(SITE, event("done", "jira-assignee"));

        ArgumentCaptor<Supplier<List<String>>> comments = ArgumentCaptor.forClass(Supplier.class);
        ArgumentCaptor<Consumer<SolutionSections>> apply = ArgumentCaptor.forClass(Consumer.class);
        verify(formAi).startDraft(eq("VPN drops"), eq("Reconnect fails."), comments.capture(), apply.capture());
        assertThat(comments.getValue().get()).containsExactly("Restarted");
        SolutionSections ai = new SolutionSections("", "Old client", "Update", "", "");
        apply.getValue().accept(ai);
        verify(drafts).replaceUnsavedDraft(ASSIGNEE.id(), PROJECT, ISSUE, ai);
    }

    @Test
    void startsNoAiDraftWhenNoDraftWasCreated_AC_005_11() throws Exception {
        given(drafts.recordIssueStatus(any())).willReturn(false);

        service.receive(SITE, event("done", "jira-assignee"));

        verifyNoInteractions(formAi, issues);
    }

    @Test
    void answersTheEventWhenTheAiPoolIsFull_AC_005_12() throws Exception {
        given(drafts.recordIssueStatus(any())).willReturn(true);
        willThrow(new RejectedExecutionException()).given(formAi).startDraft(any(), any(), any(), any());

        service.receive(SITE, event("done", "jira-assignee"));

        verify(drafts).recordIssueStatus(any());
    }

    @Test
    void createsTheMoversDraftWhenTheIssueHasNoAssignee_AC_005_7() throws Exception {
        service.receive(SITE, event("done", null));

        assertThat(recorded().draftOwner()).isEqualTo(MOVER.id());
    }

    @Test
    void createsNoDraftForAnUnlinkedOwnerANonMemberOrAnIssueWithATicket_AC_005_8() throws Exception {
        given(users.find("cloud-1", "jira-assignee")).willReturn(Optional.empty());
        service.receive(SITE, event("done", "jira-assignee"));
        assertThat(recorded()).isEqualTo(new IssueStatusChange(PROJECT, ISSUE, true, null, null));

        given(users.find("cloud-1", "jira-assignee")).willReturn(Optional.of(ASSIGNEE));
        given(scopes.canAccessProject(ASSIGNEE.id(), PROJECT)).willReturn(false);
        service.receive(SITE, event("done", "jira-assignee"));
        assertThat(lastRecorded().draftOwner()).isNull();

        given(scopes.canAccessProject(ASSIGNEE.id(), PROJECT)).willReturn(true);
        given(tickets.findStatus(ISSUE)).willReturn(Optional.of(TicketStatus.PENDING_REVIEW));
        service.receive(SITE, event("done", "jira-assignee"));
        assertThat(lastRecorded().draftOwner()).isNull();
    }

    @Test
    void createsNoDraftForAnAutomationWithoutAnAssignee_AC_005_8() throws Exception {
        given(users.find("cloud-1", "jira-mover")).willReturn(Optional.empty());

        service.receive(SITE, event("done", null));

        assertThat(recorded().draftOwner()).isNull();
    }

    @Test
    void recordsAMoveOutOfDoneAndTouchesNoDraft_AC_005_9() throws Exception {
        given(drafts.findIssueDone(PROJECT, ISSUE)).willReturn(Optional.of(true));

        service.receive(SITE, event("indeterminate", "jira-assignee"));

        assertThat(recorded()).isEqualTo(new IssueStatusChange(PROJECT, ISSUE, false, null, null));
        verifyNoInteractions(users);
    }

    @Test
    void createsNoDraftForAChangeBetweenTwoDoneStatuses_AC_005_7_AC_005_9() throws Exception {
        given(drafts.findIssueDone(PROJECT, ISSUE)).willReturn(Optional.of(true));

        service.receive(SITE, event("done", "jira-assignee"));

        assertThat(recorded()).isEqualTo(new IssueStatusChange(PROJECT, ISSUE, true, null, null));
    }

    @Test
    void asksJiraForTheStatusBeforeWhenTheIssueWasNeverRecorded_AC_005_7() throws Exception {
        given(drafts.findIssueDone(PROJECT, ISSUE)).willReturn(Optional.empty());
        given(jiraAccounts.current()).willReturn(Optional.of(APP));

        given(statuses.categoryOf(APP, "3")).willReturn(Optional.of("done"));
        service.receive(SITE, event("done", "jira-assignee"));
        assertThat(recorded().draftOwner()).isNull();

        given(statuses.categoryOf(APP, "3")).willReturn(Optional.of("indeterminate"));
        service.receive(SITE, event("done", "jira-assignee"));
        assertThat(lastRecorded().draftOwner()).isEqualTo(ASSIGNEE.id());
    }

    @Test
    void countsAFailedJiraReadAsAMoveIntoDone_AC_005_7() throws Exception {
        given(drafts.findIssueDone(PROJECT, ISSUE)).willReturn(Optional.empty());
        given(jiraAccounts.current()).willReturn(Optional.of(APP));
        given(statuses.categoryOf(APP, "3")).willReturn(Optional.empty());

        service.receive(SITE, event("done", "jira-assignee"));

        assertThat(recorded().draftOwner()).isEqualTo(ASSIGNEE.id());
    }

    @Test
    void changesNothingForAnEventItCannotReadOrAnIssueOfNoProject_AC_005_10() throws Exception {
        service.receive(SITE, new IssueUpdatedPayload("jira-mover", null, null));
        service.receive(SITE, withoutStatusChange());
        verifyNoInteractions(workspaces);

        given(projects.findActivePublicIdByJiraProjectKey("ITSUP")).willReturn(Optional.empty());
        service.receive(SITE, event("done", "jira-assignee"));

        given(workspaces.workspaceOf("cloud-1")).willReturn(Optional.empty());
        service.receive(SITE, event("done", "jira-assignee"));

        verify(drafts, never()).recordIssueStatus(any());
    }

    @Test
    void letsAFailedWriteFailTheCall_AC_005_10() throws Exception {
        willThrow(new IllegalStateException("db down")).given(drafts).recordIssueStatus(any());

        assertThatThrownBy(() -> service.receive(SITE, event("done", "jira-assignee")))
                .isInstanceOf(IllegalStateException.class);
    }

    private IssueStatusChange recorded() {
        ArgumentCaptor<IssueStatusChange> change = ArgumentCaptor.forClass(IssueStatusChange.class);
        verify(drafts).recordIssueStatus(change.capture());
        return change.getValue();
    }

    private IssueStatusChange lastRecorded() {
        ArgumentCaptor<IssueStatusChange> change = ArgumentCaptor.forClass(IssueStatusChange.class);
        verify(drafts, org.mockito.Mockito.atLeastOnce()).recordIssueStatus(change.capture());
        return change.getValue();
    }

    private static IssueUpdatedPayload event(String category, String assignee) throws Exception {
        var description = new ObjectMapper()
                .readTree("{\"type\":\"doc\",\"content\":[{\"type\":\"paragraph\","
                        + "\"content\":[{\"type\":\"text\",\"text\":\"Reconnect fails.\"}]}]}");
        Fields fields = new Fields(
                "VPN drops",
                description,
                new Status(new StatusCategory(category)),
                assignee == null ? null : new Assignee(assignee));
        return new IssueUpdatedPayload(
                "jira-mover", new Issue(ISSUE, fields), new Changelog(List.of(new Item("status", "3", "10002"))));
    }

    private static IssueUpdatedPayload withoutStatusChange() {
        Fields fields = new Fields("VPN drops", null, new Status(new StatusCategory("done")), null);
        return new IssueUpdatedPayload(
                "jira-mover", new Issue(ISSUE, fields), new Changelog(List.of(new Item("assignee", "a", "b"))));
    }
}
