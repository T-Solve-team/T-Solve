package vn.tsolve.jiraapp.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import org.junit.jupiter.api.Test;
import vn.tsolve.common.security.JiraAppPrincipal;
import vn.tsolve.common.security.TSolveUser;
import vn.tsolve.jiraapp.dto.IssuePanelResponse;
import vn.tsolve.jiraapp.dto.JiraIssueContext;
import vn.tsolve.jiraapp.dto.SolutionFormDraft;
import vn.tsolve.jiraapp.dto.SolutionFormRequest;
import vn.tsolve.jiraapp.dto.SolutionFormResponse;
import vn.tsolve.jiraapp.dto.SolutionFormResponse.Origin;
import vn.tsolve.jiraapp.dto.SubmitSolutionRequest;
import vn.tsolve.jiraapp.dto.SubmittedTicketResponse;
import vn.tsolve.jiraapp.exception.JiraAskerNotLinkedException;
import vn.tsolve.jiraapp.exception.JiraIssueAccessDeniedException;
import vn.tsolve.jiraapp.service.JiraIssueAccess;
import vn.tsolve.jiraapp.service.SolutionFormAi;
import vn.tsolve.ticket.dto.SolutionFormSubmission;
import vn.tsolve.ticket.dto.SolutionSections;
import vn.tsolve.ticket.dto.SubmittedSolutionTicket;
import vn.tsolve.ticket.entity.TicketStatus;
import vn.tsolve.ticket.service.SolutionDraftService;
import vn.tsolve.ticket.service.SolutionFormTicketService;

class JiraAppSolutionServiceImplTest {

    private static final String ISSUE = "ITSUP-12";
    private static final UUID PROJECT = UUID.fromString("00000000-0000-0000-0000-000000000126");
    private static final UUID TICKET = UUID.fromString("00000000-0000-0000-0000-0000000000aa");
    private static final TSolveUser USER =
            new TSolveUser(UUID.fromString("00000000-0000-0000-0000-000000000001"), "b@x.test", "Ngo Bao", Set.of());
    private static final JiraAppPrincipal CALLER = new JiraAppPrincipal("cloud-1", "jira-1", null);
    private static final SolutionSections SECTIONS = new SolutionSections("Drops", "Old", "Update", "", "Laptops");
    private static final SolutionFormRequest ISSUE_TEXT = new SolutionFormRequest("VPN drops", "After sleep");

    private final JiraIssueAccess access = mock(JiraIssueAccess.class);
    private final SolutionFormTicketService tickets = mock(SolutionFormTicketService.class);
    private final SolutionDraftService drafts = mock(SolutionDraftService.class);
    private final SolutionFormAi ai = mock(SolutionFormAi.class);
    private final JiraAppSolutionServiceImpl service = new JiraAppSolutionServiceImpl(access, tickets, drafts, ai);

    @Test
    void opensWithTheUsersDraftFirst_AC_005_2() {
        givenContext(member());
        given(drafts.find(USER.id(), PROJECT, ISSUE)).willReturn(Optional.of(SECTIONS));

        SolutionFormResponse form = service.openForm(CALLER, ISSUE, ISSUE_TEXT);

        assertThat(form).isEqualTo(new SolutionFormResponse(true, Origin.DRAFT, SECTIONS, null));
        verify(ai, never()).draft(anyString(), anyString());
    }

    @Test
    void opensWithTheAiDraftWithoutADraft_AC_002_1() {
        givenContext(member());
        given(drafts.find(USER.id(), PROJECT, ISSUE)).willReturn(Optional.empty());
        given(ai.draft("VPN drops", "After sleep")).willReturn(new SolutionFormDraft(SECTIONS, true));

        SolutionFormResponse form = service.openForm(CALLER, ISSUE, ISSUE_TEXT);

        assertThat(form.origin()).isEqualTo(Origin.AI_DRAFT);
        assertThat(form.message()).isNull();
    }

    @Test
    void opensWithTheIssueTextAndMsg67WhenTheAiIsUnavailable_AC_002_2() {
        givenContext(member());
        given(drafts.find(USER.id(), PROJECT, ISSUE)).willReturn(Optional.empty());
        SolutionSections issueText = new SolutionSections("VPN drops\n\nAfter sleep", "", "", "", "");
        given(ai.draft("VPN drops", "After sleep")).willReturn(new SolutionFormDraft(issueText, false));

        SolutionFormResponse form = service.openForm(CALLER, ISSUE, ISSUE_TEXT);

        assertThat(form.origin()).isEqualTo(Origin.ISSUE);
        assertThat(form.sections()).isEqualTo(issueText);
        assertThat(form.message().code()).isEqualTo("MSG67");
    }

    @Test
    void doesNotOpenForANonMemberOrAnIssueWithATicket_AC_001_2_AC_001_5() {
        givenContext(new JiraIssueContext(Optional.of(USER), Optional.of(PROJECT), false));
        assertThat(service.openForm(CALLER, ISSUE, ISSUE_TEXT).mayOpen()).isFalse();

        givenContext(member());
        given(tickets.findStatus(ISSUE)).willReturn(Optional.of(TicketStatus.PUBLISHED));
        assertThat(service.openForm(CALLER, ISSUE, ISSUE_TEXT).mayOpen()).isFalse();
        verify(ai, never()).draft(anyString(), anyString());
    }

    @Test
    void submitsAsTheLinkedMemberOfTheProject_AC_004_1() {
        givenContext(member());
        given(tickets.submit(any()))
                .willReturn(new SubmittedSolutionTicket(TICKET, ISSUE, TicketStatus.PENDING_REVIEW));

        SubmittedTicketResponse submitted =
                service.submit(CALLER, ISSUE, new SubmitSolutionRequest("VPN drops", SECTIONS));

        assertThat(submitted).isEqualTo(new SubmittedTicketResponse(TICKET, ISSUE, TicketStatus.PENDING_REVIEW));
        verify(tickets).submit(new SolutionFormSubmission(PROJECT, ISSUE, "VPN drops", USER.id(), "Ngo Bao", SECTIONS));
    }

    @Test
    void refusesToSubmitForANonMember_AC_001_2() {
        givenContext(new JiraIssueContext(Optional.of(USER), Optional.of(PROJECT), false));

        assertThatThrownBy(() -> service.submit(CALLER, ISSUE, new SubmitSolutionRequest("VPN", SECTIONS)))
                .isInstanceOf(JiraIssueAccessDeniedException.class);
        verify(tickets, never()).submit(any());
    }

    @Test
    void savesTheDraftOfTheLinkedMember_AC_005_1() {
        givenContext(member());

        service.saveDraft(CALLER, ISSUE, SECTIONS);

        verify(drafts).save(USER.id(), PROJECT, ISSUE, SECTIONS);
    }

    @Test
    void refusesToSaveADraftForAnUnlinkedAccount_AC_008_1() {
        givenContext(new JiraIssueContext(Optional.empty(), Optional.of(PROJECT), false));

        assertThatThrownBy(() -> service.saveDraft(CALLER, ISSUE, SECTIONS))
                .isInstanceOf(JiraAskerNotLinkedException.class);
    }

    @Test
    void showsOnlyMsg62ForAnUnlinkedAccount_AC_008_1() {
        givenContext(new JiraIssueContext(Optional.empty(), Optional.of(PROJECT), false));

        assertThat(service.panel(CALLER, ISSUE)).isEqualTo(IssuePanelResponse.notLinked());
    }

    @Test
    void showsTheDraftAndTheFormButtonWhileNoTicketExists_AC_001_4_AC_008_3() {
        givenContext(member());
        given(drafts.find(USER.id(), PROJECT, ISSUE)).willReturn(Optional.of(SECTIONS));

        assertThat(service.panel(CALLER, ISSUE)).isEqualTo(new IssuePanelResponse(true, null, true, true));
    }

    @Test
    void showsTheTicketStatusAndNoFormButtonOnceSubmitted_AC_008_2() {
        givenContext(member());
        given(tickets.findStatus(ISSUE)).willReturn(Optional.of(TicketStatus.PENDING_REVIEW));

        assertThat(service.panel(CALLER, ISSUE))
                .isEqualTo(new IssuePanelResponse(true, TicketStatus.PENDING_REVIEW, false, false));
    }

    @Test
    void rewritesForALinkedUser_AC_007_1() {
        given(access.asUser(eq(CALLER), any())).willAnswer(call -> {
            Function<TSolveUser, Object> work = call.getArgument(1);
            return work.apply(USER);
        });
        SolutionSections rewritten = new SolutionSections("Drops.", "Old.", "Update.", "", "Laptops.");
        given(ai.rewrite(SECTIONS)).willReturn(rewritten);

        assertThat(service.rewrite(CALLER, SECTIONS)).isEqualTo(rewritten);
    }

    private static JiraIssueContext member() {
        return new JiraIssueContext(Optional.of(USER), Optional.of(PROJECT), true);
    }

    private void givenContext(JiraIssueContext context) {
        given(access.inIssue(eq(CALLER), eq(ISSUE), any())).willAnswer(call -> {
            Function<JiraIssueContext, Object> work = call.getArgument(2);
            return work.apply(context);
        });
    }
}
