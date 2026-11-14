package vn.tsolve.ticket.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import vn.tsolve.common.exception.CodedException;
import vn.tsolve.ticket.dto.SolutionFormSubmission;
import vn.tsolve.ticket.dto.SolutionSections;
import vn.tsolve.ticket.dto.SubmittedSolutionTicket;
import vn.tsolve.ticket.entity.TicketStatus;
import vn.tsolve.ticket.exception.ProjectNotActiveException;
import vn.tsolve.ticket.exception.TicketAlreadyExistsException;
import vn.tsolve.ticket.repository.SolutionDraftRepository;
import vn.tsolve.ticket.repository.TicketRepository;

class SolutionFormTicketServiceImplTest {

    private static final UUID PROJECT = UUID.fromString("00000000-0000-0000-0000-000000000126");
    private static final UUID WRITER = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID TICKET = UUID.fromString("00000000-0000-0000-0000-0000000000aa");
    private static final String ISSUE = "ITSUP-12";
    // 18:00 UTC on 10/11 is already 11/11 in Vietnam: the due date counts from the Vietnam day (GBR-10).
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-10T18:00:00Z"), ZoneOffset.UTC);
    private static final LocalDate VIETNAM_TODAY = LocalDate.of(2026, 11, 11);

    private final TicketRepository tickets = mock(TicketRepository.class);
    private final SolutionDraftRepository drafts = mock(SolutionDraftRepository.class);
    private final SolutionFormTicketServiceImpl service = new SolutionFormTicketServiceImpl(tickets, drafts, CLOCK);

    @Test
    void createsAPendingReviewTicketAndDeletesTheDraft_AC_004_1() {
        givenInserted();

        SubmittedSolutionTicket ticket = service.submit(submission("VPN drops", full()));

        assertThat(ticket).isEqualTo(new SubmittedSolutionTicket(TICKET, ISSUE, TicketStatus.PENDING_REVIEW));
        verify(tickets)
                .insertSolutionFormTicket(
                        PROJECT,
                        ISSUE,
                        "VPN drops",
                        "Ngo Bao",
                        WRITER,
                        "Drops hourly",
                        "Old client",
                        "Update to 5.2",
                        null,
                        "Windows laptops",
                        VIETNAM_TODAY);
        verify(drafts).deleteDraft(WRITER, PROJECT, ISSUE);
    }

    @Test
    void cutsALongSummaryToTheTitleColumn_AC_004_1() {
        givenInserted();
        String summary = "😀".repeat(250);

        service.submit(submission(summary, full()));

        verify(tickets)
                .insertSolutionFormTicket(
                        any(), any(), eq("😀".repeat(200)), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void refusesAnEmptyRequiredSectionWithMsg01OnThatSection_AC_002_5() {
        SolutionSections missingRootCause = new SolutionSections("Drops hourly", "  ", "Update", "", "Laptops");

        assertThatThrownBy(() -> service.submit(submission("VPN", missingRootCause)))
                .isInstanceOfSatisfying(CodedException.class, e -> {
                    assertThat(e.getCode()).isEqualTo("MSG01");
                    assertThat(e.getField()).isEqualTo("rootCause");
                    assertThat(e.getParams()).containsEntry("field_name", "Root cause");
                });
        verifyNoInteractions(tickets, drafts);
    }

    @Test
    void acceptsExactlyTenThousandCodePointsAndRefusesOneMoreWithMsg02_AC_002_5() {
        givenInserted();
        String emoji = "😀";
        SolutionSections atLimit = new SolutionSections(emoji.repeat(9_997), "a", "b", "", "c");
        SolutionSections overLimit = new SolutionSections(emoji.repeat(9_998), "a", "b", "", "c");

        service.submit(submission("VPN", atLimit));

        assertThatThrownBy(() -> service.submit(submission("VPN", overLimit)))
                .isInstanceOfSatisfying(
                        CodedException.class, e -> assertThat(e.getCode()).isEqualTo("MSG02"));
    }

    @Test
    void refusesAnAllEmptySolutionSoItNeverReachesTheReviewQueue_AC_005_5() {
        assertThatThrownBy(() -> service.submit(submission("VPN", new SolutionSections(null, "", " ", null, ""))))
                .isInstanceOfSatisfying(
                        CodedException.class, e -> assertThat(e.getCode()).isEqualTo("MSG01"));

        verify(tickets, never())
                .insertSolutionFormTicket(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
        verify(drafts, never()).deleteDraft(any(), any(), any());
    }

    @Test
    void answers409WhenTheIssueAlreadyHasATicket_AC_004_3() {
        givenNothingInserted();
        given(tickets.findStatusByTicketId(ISSUE)).willReturn(Optional.of(TicketStatus.PENDING_REVIEW));

        assertThatThrownBy(() -> service.submit(submission("VPN", full())))
                .isInstanceOf(TicketAlreadyExistsException.class);
        verify(drafts, never()).deleteDraft(any(), any(), any());
    }

    @Test
    void refusesWhenTheProjectIsNoLongerActive_AC_004_1() {
        givenNothingInserted();
        given(tickets.findStatusByTicketId(ISSUE)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.submit(submission("VPN", full())))
                .isInstanceOf(ProjectNotActiveException.class);
    }

    @Test
    void readsTheStatusOfTheIssuesTicket_AC_008_2() {
        given(tickets.findStatusByTicketId(ISSUE)).willReturn(Optional.of(TicketStatus.CHANGES_REQUESTED));

        assertThat(service.findStatus(ISSUE)).contains(TicketStatus.CHANGES_REQUESTED);
    }

    private void givenInserted() {
        given(tickets.insertSolutionFormTicket(
                        any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .willReturn(Optional.of(TICKET));
    }

    private void givenNothingInserted() {
        given(tickets.insertSolutionFormTicket(
                        any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .willReturn(Optional.empty());
    }

    private static SolutionSections full() {
        return new SolutionSections("Drops hourly", "Old client", "Update to 5.2", " ", "Windows laptops");
    }

    private static SolutionFormSubmission submission(String summary, SolutionSections sections) {
        return new SolutionFormSubmission(PROJECT, ISSUE, summary, WRITER, "Ngo Bao", sections);
    }
}
