package vn.tsolve.ticket.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;
import vn.tsolve.common.audit.AuditAction;
import vn.tsolve.common.audit.AuditService;
import vn.tsolve.common.exception.MessageCodeException;
import vn.tsolve.common.security.Role;
import vn.tsolve.common.security.TSolveUser;
import vn.tsolve.ticket.dto.ManualEntryResponse;
import vn.tsolve.ticket.entity.ImportBatch;
import vn.tsolve.ticket.entity.ImportBatchStatus;
import vn.tsolve.ticket.entity.ReviewDecision;
import vn.tsolve.ticket.entity.Ticket;
import vn.tsolve.ticket.entity.TicketReviewDecision;
import vn.tsolve.ticket.entity.TicketSource;
import vn.tsolve.ticket.entity.TicketStatus;
import vn.tsolve.ticket.event.TicketsPublishedEvent;
import vn.tsolve.ticket.repository.ImportBatchRepository;
import vn.tsolve.ticket.repository.TicketRepository;
import vn.tsolve.ticket.repository.TicketReviewDecisionRepository;
import vn.tsolve.ticket.service.ProjectAccess;

class ManualEntryServiceImplTest {

    private static final UUID PROJECT = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID PM_ID = UUID.fromString("00000000-0000-0000-0000-0000000000aa");
    private static final UUID TICKET = UUID.fromString("00000000-0000-0000-0000-000000000101");
    private static final TSolveUser PM =
            new TSolveUser(PM_ID, "pm@company.vn", "Nguyen Van An", Set.of(Role.PROJECT_MANAGER));

    private final ProjectAccess projects = mock(ProjectAccess.class);
    private final TicketRepository tickets = mock(TicketRepository.class);
    private final TicketReviewDecisionRepository decisions = mock(TicketReviewDecisionRepository.class);
    private final ImportBatchRepository batches = mock(ImportBatchRepository.class);
    private final ImportBatchRecorder recorder = mock(ImportBatchRecorder.class);
    private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
    private final AuditService audit = mock(AuditService.class);
    private final List<String> transactions = new ArrayList<>();

    /** Runs the callback like a real transaction would, and records whether it committed or rolled back. */
    private final PlatformTransactionManager txManager = new PlatformTransactionManager() {
        @Override
        public TransactionStatus getTransaction(TransactionDefinition definition) {
            return new SimpleTransactionStatus();
        }

        @Override
        public void commit(TransactionStatus status) {
            transactions.add("commit");
        }

        @Override
        public void rollback(TransactionStatus status) {
            transactions.add("rollback");
        }
    };

    private final ManualEntryServiceImpl service = new ManualEntryServiceImpl(
            new ObjectMapper(),
            new ManualEntryValidator(Clock.fixed(Instant.parse("2026-11-14T03:00:00Z"), ZoneOffset.UTC)),
            projects,
            tickets,
            decisions,
            batches,
            recorder,
            events,
            txManager,
            audit);

    @BeforeEach
    void setUp() {
        when(projects.managesActive(PM_ID.toString(), PROJECT)).thenReturn(true);
        when(tickets.findExistingTicketIds(anyCollection())).thenReturn(Set.of());
        when(batches.save(any(ImportBatch.class))).thenAnswer(call -> {
            ImportBatch batch = call.getArgument(0);
            ReflectionTestUtils.setField(batch, "id", 7L);
            return batch;
        });
        when(tickets.save(any(Ticket.class))).thenAnswer(call -> {
            Ticket ticket = call.getArgument(0);
            ReflectionTestUtils.setField(ticket, "id", TICKET);
            return ticket;
        });
    }

    private static String json(Map<String, Object> changes) {
        Map<String, Object> fields = new HashMap<>(Map.of(
                "projectId", PROJECT.toString(),
                "ticketId", "  T-1 ",
                "title", " Login fails ",
                "description", "Users cannot sign in",
                "resolvedDate", "2026-11-10",
                "symptoms", " Error 500 ",
                "rootCause", "Bad token",
                "resolutionSteps", "Restart",
                "scope", "All users"));
        fields.putAll(changes);
        try {
            return new ObjectMapper().writeValueAsString(fields);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String json() {
        return json(Map.of());
    }

    private MessageCodeException rejection(String entry) {
        return catchThrowableOfType(() -> service.save(PM, entry), MessageCodeException.class);
    }

    private void assertNothingSaved() {
        verify(tickets, never()).save(any(Ticket.class));
        verifyNoInteractions(decisions, batches, recorder, events, audit);
    }

    @Test
    void savesAPublishedManualTicketWithItsWriterDecisionAndBatch_AC_006_1() {
        ManualEntryResponse response = service.save(PM, json());

        assertThat(response.ticketId()).isEqualTo(TICKET);
        assertThat(response.message().code()).isEqualTo("MSG32");
        assertThat(response.message().params()).containsEntry("created", 1).containsEntry("skipped", 0);

        ArgumentCaptor<Ticket> ticket = ArgumentCaptor.forClass(Ticket.class);
        verify(tickets).save(ticket.capture());
        Ticket saved = ticket.getValue();
        assertThat(saved.getSource()).isEqualTo(TicketSource.MANUAL);
        assertThat(saved.getStatus()).isEqualTo(TicketStatus.PUBLISHED);
        assertThat(saved.getProjectId()).isEqualTo(PROJECT);
        assertThat(saved.getTicketId()).isEqualTo("T-1");
        assertThat(saved.getTitle()).isEqualTo("Login fails");
        assertThat(saved.getDescription()).isEqualTo("Users cannot sign in");
        assertThat(saved.getResolvedDate()).isEqualTo(LocalDate.of(2026, 11, 10));
        assertThat(saved.getWriter()).isEqualTo("Nguyen Van An");
        assertThat(saved.getWriterId()).isEqualTo(PM_ID);
        assertThat(saved.getImportBatchId()).isEqualTo(7L);
        assertThat(saved.getSolution())
                .isEqualTo(new Ticket.Solution("Error 500", "Bad token", "Restart", null, "All users"));

        ArgumentCaptor<TicketReviewDecision> decision = ArgumentCaptor.forClass(TicketReviewDecision.class);
        verify(decisions).save(decision.capture());
        assertThat(decision.getValue().getTicketId()).isEqualTo(TICKET);
        assertThat(decision.getValue().getReviewer()).isEqualTo(PM_ID.toString());
        assertThat(decision.getValue().getDecision()).isEqualTo(ReviewDecision.APPROVE);

        ArgumentCaptor<ImportBatch> batch = ArgumentCaptor.forClass(ImportBatch.class);
        verify(batches).save(batch.capture());
        assertThat(batch.getValue().getSource()).isEqualTo(TicketSource.MANUAL);
        assertThat(batch.getValue().getStatus()).isEqualTo(ImportBatchStatus.COMPLETED);
        assertThat(batch.getValue().getProjectId()).isEqualTo(PROJECT);
        assertThat(batch.getValue().getPerformedBy()).isEqualTo(PM_ID.toString());
        assertThat(batch.getValue().getPublishedCount()).isEqualTo(1);
        assertThat(batch.getValue().getFileName()).isNull();

        verify(audit).record(AuditAction.IMPORT, "PROJECT", PROJECT, Map.of("importBatchId", 7L, "created", 1));
        assertThat(transactions).containsExactly("commit");
        verifyNoInteractions(recorder);
    }

    @Test
    void aTicketWithoutAResolvedDateIsSaved_AC_006_1() {
        Map<String, Object> changes = new HashMap<>();
        changes.put("resolvedDate", null);

        service.save(PM, json(changes));

        ArgumentCaptor<Ticket> ticket = ArgumentCaptor.forClass(Ticket.class);
        verify(tickets).save(ticket.capture());
        assertThat(ticket.getValue().getResolvedDate()).isNull();
    }

    @Test
    void publishesOneTicketsPublishedEventInsideTheTransaction_AC_006_10() {
        service.save(PM, json());

        verify(events).publishEvent(new TicketsPublishedEvent(List.of(TICKET)));
        assertThat(transactions).containsExactly("commit");
    }

    @Test
    void anEntryThatFailsACheckSavesNothingAndRecordsNoBatch_AC_006_3() {
        MessageCodeException e = rejection(json(Map.of("title", "  ")));

        assertThat(e.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(e.getCode()).isEqualTo("MSG01");
        assertNothingSaved();
        // checked before the Project, so a rejected entry never asks who manages it
        verifyNoInteractions(projects);
    }

    @Test
    void anEntryThatIsNotJsonIs400Msg06_AC_006_3() {
        MessageCodeException e = rejection("{\"projectId\":");

        assertThat(e.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(e.getCode()).isEqualTo("MSG06");
        assertNothingSaved();
    }

    @Test
    void aTakenTicketIdIs409Msg31BeforeAnythingIsSaved_AC_006_8() {
        when(tickets.findExistingTicketIds(List.of("T-1"))).thenReturn(Set.of("T-1"));

        MessageCodeException e = rejection(json());

        assertThat(e.getStatus()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(e.getCode()).isEqualTo("MSG31");
        assertThat(e.getParams()).containsEntry("ticket_id", "T-1");
        assertNothingSaved();
        assertThat(transactions).isEmpty();
    }

    @Test
    void aTicketIdTakenWhileSavingIs409Msg31WithNoBatch_AC_006_8() {
        // another request saved the same Ticket ID after the pre-check: the unique constraint stops this one
        when(tickets.save(any(Ticket.class)))
                .thenThrow(new DataIntegrityViolationException(
                        "could not execute statement",
                        new SQLException(
                                "ERROR: duplicate key value violates unique constraint \"uq_ticket_ticket_id\"",
                                "23505")));

        MessageCodeException e = rejection(json());

        assertThat(e.getStatus()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(e.getCode()).isEqualTo("MSG31");
        assertThat(e.getParams()).containsEntry("ticket_id", "T-1");
        assertThat(transactions).containsExactly("rollback");
        verifyNoInteractions(recorder, events);
    }

    @Test
    void aProjectTheCallerDoesNotManageOrThatIsNotActiveIs403_AC_006_9() {
        when(projects.managesActive(PM_ID.toString(), PROJECT)).thenReturn(false);

        MessageCodeException e = rejection(json());

        assertThat(e.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(e.getCode()).isEqualTo("MSG08");
        assertNothingSaved();
        verify(tickets, never()).findExistingTicketIds(anyCollection());
    }

    @Test
    void aFailureWhileSavingIsMsg33WithAFailedManualBatch_AC_006_11() {
        when(decisions.save(any(TicketReviewDecision.class))).thenThrow(new QueryTimeoutException("timeout"));

        MessageCodeException e = rejection(json());

        assertThat(e.getStatus()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(e.getCode()).isEqualTo("MSG33");
        assertThat(transactions).containsExactly("rollback");
        ArgumentCaptor<String> error = ArgumentCaptor.forClass(String.class);
        verify(recorder)
                .recordFailedWithoutPreview(
                        eq(PROJECT), eq(PM_ID.toString()), eq(TicketSource.MANUAL), error.capture());
        // the error names the failure only, never ticket content
        assertThat(error.getValue())
                .contains("QueryTimeoutException")
                .contains("MSG33")
                .doesNotContain("T-1")
                .doesNotContain("Login fails");
        verifyNoInteractions(events);
    }

    @Test
    void anotherConstraintViolationIsAFailureNotATakenTicketId_AC_006_11() {
        when(tickets.save(any(Ticket.class)))
                .thenThrow(new DataIntegrityViolationException(
                        "could not execute statement",
                        new SQLException("ERROR: value too long for type character varying(200)", "22001")));

        MessageCodeException e = rejection(json());

        assertThat(e.getCode()).isEqualTo("MSG33");
        verify(recorder)
                .recordFailedWithoutPreview(eq(PROJECT), eq(PM_ID.toString()), eq(TicketSource.MANUAL), anyString());
    }

    @Test
    void aFailureToRecordTheFailedBatchStillAnswersMsg33_AC_006_11() {
        when(decisions.save(any(TicketReviewDecision.class))).thenThrow(new QueryTimeoutException("timeout"));
        doThrow(new QueryTimeoutException("down"))
                .when(recorder)
                .recordFailedWithoutPreview(any(), anyString(), any(), anyString());

        assertThat(rejection(json()).getCode()).isEqualTo("MSG33");
    }
}
