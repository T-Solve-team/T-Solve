package vn.tsolve.ticket.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mapstruct.factory.Mappers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.projection.SpelAwareProxyProjectionFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;
import vn.tsolve.common.exception.NotFoundException;
import vn.tsolve.identity.service.ScopeAccessService;
import vn.tsolve.ticket.dto.TicketReviewView.Project;
import vn.tsolve.ticket.dto.TicketReviewView.ProjectAccess;
import vn.tsolve.ticket.entity.Ticket;
import vn.tsolve.ticket.entity.TicketSource;
import vn.tsolve.ticket.entity.TicketStatus;
import vn.tsolve.ticket.mapper.TicketReviewMapper;
import vn.tsolve.ticket.repository.TicketDecisionRepository;
import vn.tsolve.ticket.repository.TicketEvidenceRepository;
import vn.tsolve.ticket.repository.TicketRepository;
import vn.tsolve.ticket.repository.TicketRepository.Placement;

@ExtendWith(MockitoExtension.class)
class TicketDetailVisibilityTest {
    private static final UUID ID = UUID.randomUUID();
    private static final UUID PROJECT = UUID.randomUUID();
    private static final UUID DEPARTMENT = UUID.randomUUID();
    private static final UUID CALLER = UUID.randomUUID();

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

    @ParameterizedTest(name = "AC-008.1 allows Department scope without Project access, manager = {0}")
    @ValueSource(booleans = {false, true})
    void publishedDepartmentReader(boolean manager) {
        ticket(TicketStatus.PUBLISHED, null);
        when(scopeAccess.canAccessDepartment(CALLER, DEPARTMENT)).thenReturn(true);
        assertThat(reads.detail(Map.of(), CALLER, manager, ID).ticketId()).isEqualTo(ID);
    }

    @ParameterizedTest(name = "AC-008.1 AC-008.4 denies Published content even to its writer, manager = {0}")
    @ValueSource(booleans = {false, true})
    void publishedOutsideDepartment(boolean manager) {
        ticket(TicketStatus.PUBLISHED, CALLER);
        assertThatThrownBy(() -> reads.detail(projects(true), CALLER, manager, ID))
                .isInstanceOf(AccessDeniedException.class);
    }

    @ParameterizedTest(name = "AC-008.2 lets the writer read {0} without Project access")
    @EnumSource(value = TicketStatus.class, names = "PUBLISHED", mode = EnumSource.Mode.EXCLUDE)
    void nonPublishedWriter(TicketStatus status) {
        ticket(status, CALLER);
        var detail = reads.detail(Map.of(), CALLER, false, ID);
        assertThat(detail.ticketId()).isEqualTo(ID);
        assertThat(detail.canDecide()).isFalse();
        verifyNoInteractions(scopeAccess);
    }

    @ParameterizedTest(name = "AC-008.2 keeps the Project Manager access and decision permission for {0}")
    @EnumSource(value = TicketStatus.class, names = "PUBLISHED", mode = EnumSource.Mode.EXCLUDE)
    void nonPublishedProjectManager(TicketStatus status) {
        ticket(status, null);
        assertThat(reads.detail(projects(true), CALLER, true, ID).canDecide())
                .isEqualTo(status == TicketStatus.PENDING_REVIEW);
        verifyNoInteractions(scopeAccess);
    }

    @Test
    void nonPublishedProjectManagerOfAnInactiveProject_AC_008_2_BUG_312() {
        // an inactive Project is missing from the caller's active Projects, but its membership still counts
        ticket(TicketStatus.UNPUBLISHED, null);
        when(tickets.isProjectManager(PROJECT, CALLER)).thenReturn(true);
        assertThat(reads.detail(Map.of(), CALLER, true, ID).ticketId()).isEqualTo(ID);
    }

    @Test
    @DisplayName("AC-008.2 AC-008.4 denies a Department manager who does not manage this Project")
    void pendingOtherProjectManager() {
        ticket(TicketStatus.PENDING_REVIEW, null);
        assertThatThrownBy(() -> reads.detail(projects(false), CALLER, true, ID))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("AC-008.3 denies an inactive or deleted Department to the writer and Project Manager")
    void inactiveDepartment() {
        ticket(TicketStatus.PENDING_REVIEW, CALLER);
        when(tickets.findPlacement(PROJECT)).thenReturn(Optional.of(mock(Placement.class)));
        assertThatThrownBy(() -> reads.detail(projects(true), CALLER, true, ID))
                .isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(scopeAccess, evidence);
    }

    @Test
    @DisplayName("AC-008.3 denies a ticket with no Project or Department row")
    void missingPlacement() {
        ticket(TicketStatus.PUBLISHED, CALLER);
        when(tickets.findPlacement(PROJECT)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> reads.detail(projects(true), CALLER, true, ID))
                .isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(scopeAccess, evidence);
    }

    @Test
    @DisplayName("AC-008.5 returns MSG04 for an unknown ticket before checking scope")
    void unknownTicket() {
        assertThatThrownBy(() -> reads.detail(Map.of(), CALLER, false, ID))
                .isInstanceOf(NotFoundException.class)
                .hasFieldOrPropertyWithValue("code", "MSG04");
        verifyNoInteractions(scopeAccess, evidence);
    }

    private void ticket(TicketStatus status, UUID writerId) {
        Ticket ticket = Ticket.imported(
                "DETAIL-135",
                PROJECT,
                TicketSource.SOLUTION_FORM,
                null,
                "Login restored",
                "Description",
                "Writer",
                LocalDate.of(2026, 11, 14),
                new Ticket.Solution("Login failed", "Token", "Refresh token", null, "Users"));
        ReflectionTestUtils.setField(ticket, "id", ID);
        ReflectionTestUtils.setField(ticket, "status", status);
        ReflectionTestUtils.setField(ticket, "writerId", writerId);
        when(tickets.findForReviewDetail(ID)).thenReturn(Optional.of(ticket));
        var placement = new SpelAwareProxyProjectionFactory()
                .createProjection(
                        Placement.class,
                        Map.of(
                                "departmentActive",
                                true,
                                "projectPublicId",
                                PROJECT,
                                "projectKey",
                                "DETAIL",
                                "departmentId",
                                DEPARTMENT,
                                "departmentName",
                                "Support"));
        when(tickets.findPlacement(PROJECT)).thenReturn(Optional.of(placement));
    }

    private Map<UUID, ProjectAccess> projects(boolean managed) {
        lenient().when(tickets.isProjectManager(PROJECT, CALLER)).thenReturn(managed);
        return Map.of(PROJECT, new ProjectAccess(new Project(PROJECT, "DETAIL"), DEPARTMENT, managed));
    }
}
