package vn.tsolve.ticket.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mapstruct.factory.Mappers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import vn.tsolve.common.exception.BusinessRuleException;
import vn.tsolve.common.exception.NotFoundException;
import vn.tsolve.identity.service.ScopeAccessService;
import vn.tsolve.ticket.dto.TicketReviewView.Solution;
import vn.tsolve.ticket.entity.Ticket;
import vn.tsolve.ticket.entity.TicketSource;
import vn.tsolve.ticket.entity.TicketStatus;
import vn.tsolve.ticket.exception.ProjectAccessDeniedException;
import vn.tsolve.ticket.mapper.TicketReviewMapper;
import vn.tsolve.ticket.repository.TicketDecisionRepository;
import vn.tsolve.ticket.repository.TicketEvidenceRepository;
import vn.tsolve.ticket.repository.TicketRepository;

/** The text AI check reads, behind the same scope as the decisions (AC-015.7, AC-009.3). */
@ExtendWith(MockitoExtension.class)
class TicketReviewTextTest {
    private static final UUID ID = UUID.randomUUID();
    private static final UUID PROJECT = UUID.randomUUID();

    @Mock
    private TicketRepository tickets;

    @Mock
    private TicketEvidenceRepository evidence;

    @Mock
    private TicketDecisionRepository decisions;

    @Mock
    private ScopeAccessService scopeAccess;

    private TicketReviewReadServiceImpl reads;

    @BeforeEach
    void service() {
        reads = new TicketReviewReadServiceImpl(
                tickets, evidence, decisions, Mappers.getMapper(TicketReviewMapper.class), scopeAccess);
    }

    @Test
    void returnsTitleDescriptionAndSolutionOfAPendingSolutionFormTicket_AC_015_2() {
        ticket(TicketSource.SOLUTION_FORM, TicketStatus.PENDING_REVIEW);

        var text = reads.reviewText(Set.of(PROJECT), ID);

        assertThat(text.title()).isEqualTo("Login restored");
        assertThat(text.description()).isEqualTo("Description");
        assertThat(text.solution())
                .isEqualTo(new Solution("Login failed", "Token", "Refresh token", "Re-login", "Users"));
    }

    @Test
    void refusesAMissingOrOtherWorkspaceTicketWith404_AC_015_7() {
        when(tickets.findForReviewDetail(ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> reads.reviewText(Set.of(PROJECT), ID))
                .isInstanceOf(NotFoundException.class)
                .hasFieldOrPropertyWithValue("code", "MSG04");
    }

    @Test
    void refusesAnUnmanagedProjectWith403_AC_015_7() {
        ticket(TicketSource.SOLUTION_FORM, TicketStatus.PENDING_REVIEW);

        assertThatThrownBy(() -> reads.reviewText(Set.of(UUID.randomUUID()), ID))
                .isInstanceOf(ProjectAccessDeniedException.class);
        assertThatThrownBy(() -> reads.reviewText(Set.of(), ID)).isInstanceOf(ProjectAccessDeniedException.class);
    }

    @ParameterizedTest(name = "AC-015.7 refuses a {0} ticket with 409")
    @EnumSource(value = TicketStatus.class, names = "PENDING_REVIEW", mode = EnumSource.Mode.EXCLUDE)
    void refusesATicketNotPendingReviewWith409_AC_015_7(TicketStatus status) {
        ticket(TicketSource.SOLUTION_FORM, status);

        assertThatThrownBy(() -> reads.reviewText(Set.of(PROJECT), ID))
                .isInstanceOf(BusinessRuleException.class)
                .hasFieldOrPropertyWithValue("code", "MSG06");
    }

    @ParameterizedTest(name = "AC-015.7 refuses a pending {0} ticket with 409")
    @EnumSource(value = TicketSource.class, names = "SOLUTION_FORM", mode = EnumSource.Mode.EXCLUDE)
    void refusesAnImportedTicketWith409_AC_015_7(TicketSource source) {
        ticket(source, TicketStatus.PENDING_REVIEW);

        assertThatThrownBy(() -> reads.reviewText(Set.of(PROJECT), ID))
                .isInstanceOf(BusinessRuleException.class)
                .hasFieldOrPropertyWithValue("code", "MSG06");
    }

    private void ticket(TicketSource source, TicketStatus status) {
        Ticket ticket = Ticket.imported(
                "AI-15",
                PROJECT,
                source,
                null,
                "Login restored",
                "Description",
                "Writer",
                LocalDate.of(2026, 11, 25),
                new Ticket.Solution("Login failed", "Token", "Refresh token", "Re-login", "Users"));
        ReflectionTestUtils.setField(ticket, "id", ID);
        ReflectionTestUtils.setField(ticket, "status", status);
        when(tickets.findForReviewDetail(ID)).thenReturn(Optional.of(ticket));
    }
}
