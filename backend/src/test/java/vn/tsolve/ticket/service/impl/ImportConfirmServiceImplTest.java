package vn.tsolve.ticket.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
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
import vn.tsolve.common.error.MessageCodeException;
import vn.tsolve.common.error.NotFoundException;
import vn.tsolve.ticket.dto.ImportResultResponse;
import vn.tsolve.ticket.dto.MessageRef;
import vn.tsolve.ticket.entity.ImportBatch;
import vn.tsolve.ticket.entity.ImportBatchStatus;
import vn.tsolve.ticket.entity.ImportPreview;
import vn.tsolve.ticket.entity.ImportPreviewRow;
import vn.tsolve.ticket.entity.PreviewRowStatus;
import vn.tsolve.ticket.entity.ReviewDecision;
import vn.tsolve.ticket.entity.Ticket;
import vn.tsolve.ticket.entity.TicketReviewDecision;
import vn.tsolve.ticket.entity.TicketSource;
import vn.tsolve.ticket.entity.TicketStatus;
import vn.tsolve.ticket.event.TicketsPublishedEvent;
import vn.tsolve.ticket.repository.ImportBatchRepository;
import vn.tsolve.ticket.repository.ImportPreviewRepository;
import vn.tsolve.ticket.repository.ImportPreviewRowRepository;
import vn.tsolve.ticket.repository.TicketRepository;
import vn.tsolve.ticket.repository.TicketReviewDecisionRepository;
import vn.tsolve.ticket.service.ProjectAccess;

class ImportConfirmServiceImplTest {

    private static final String PM = "pm@company.vn";
    private static final long PREVIEW = 42L;
    private static final long PROJECT = 1L;

    private final ImportPreviewRepository previews = mock(ImportPreviewRepository.class);
    private final ImportPreviewRowRepository rows = mock(ImportPreviewRowRepository.class);
    private final TicketRepository tickets = mock(TicketRepository.class);
    private final TicketReviewDecisionRepository decisions = mock(TicketReviewDecisionRepository.class);
    private final ImportBatchRepository batches = mock(ImportBatchRepository.class);
    private final ImportBatchRecorder recorder = mock(ImportBatchRecorder.class);
    private final ProjectAccess projects = mock(ProjectAccess.class);
    private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
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

    private final ImportConfirmServiceImpl service = new ImportConfirmServiceImpl(
            previews, rows, tickets, decisions, batches, recorder, projects, events, txManager);

    private static ImportPreviewRow row(int rowNo, String ticketId, String author, String date) {
        return new ImportPreviewRow(
                PREVIEW,
                rowNo,
                new ImportPreviewRow.Values(
                        " " + ticketId + " ",
                        " Login fails ",
                        "Long story",
                        author,
                        date,
                        " Error 500 ",
                        "Bad token",
                        "Restart",
                        null,
                        "All users"),
                List.of());
    }

    @BeforeEach
    void setUp() {
        ImportPreview preview = new ImportPreview(PM, PROJECT, TicketSource.CSV, "tickets.csv", Instant.EPOCH);
        ReflectionTestUtils.setField(preview, "id", PREVIEW);
        when(previews.findByIdAndOwner(eq(PREVIEW), anyString()))
                .thenAnswer(call -> PM.equals(call.getArgument(1)) ? Optional.of(preview) : Optional.empty());
        when(previews.lockByIdAndOwner(PREVIEW, PM)).thenReturn(Optional.of(preview));
        when(projects.managesActive(PM, PROJECT)).thenReturn(true);
        when(rows.findByIdPreviewIdAndStatus(PREVIEW, PreviewRowStatus.VALID))
                .thenReturn(List.of(row(1, "T-1", " Anh ", "2026-10-01"), row(3, "T-3", null, null)));
        when(rows.countByIdPreviewIdAndStatus(PREVIEW, PreviewRowStatus.ERROR)).thenReturn(2L);
        when(rows.countByIdPreviewIdAndStatus(PREVIEW, PreviewRowStatus.REMOVED))
                .thenReturn(1L);
        when(projects.isActive(PROJECT)).thenReturn(true);
        when(tickets.findExistingTicketIds(anyCollection())).thenReturn(Set.of());
        when(batches.save(any(ImportBatch.class))).thenAnswer(call -> {
            ImportBatch b = call.getArgument(0);
            ReflectionTestUtils.setField(b, "id", 7L);
            return b;
        });
        AtomicLong ids = new AtomicLong(100);
        when(tickets.saveAll(anyCollection())).thenAnswer(call -> {
            List<Ticket> saved = new ArrayList<>(call.getArgument(0));
            saved.forEach(t -> ReflectionTestUtils.setField(t, "id", ids.incrementAndGet()));
            return saved;
        });
    }

    @SuppressWarnings("unchecked")
    private List<Ticket> savedTickets() {
        ArgumentCaptor<List<Ticket>> captor = ArgumentCaptor.forClass(List.class);
        verify(tickets).saveAll(captor.capture());
        return captor.getValue();
    }

    @Test
    void savesEachValidRowAsAPublishedTicket_AC_004_1() {
        service.confirm(PM, PREVIEW);

        List<Ticket> saved = savedTickets();
        assertThat(saved).extracting(Ticket::getTicketId).containsExactly("T-1", "T-3");
        Ticket first = saved.get(0);
        assertThat(first.getProjectId()).isEqualTo(PROJECT);
        assertThat(first.getSource()).isEqualTo(TicketSource.CSV);
        assertThat(first.getStatus()).isEqualTo(TicketStatus.PUBLISHED);
        assertThat(first.getTitle()).isEqualTo("Login fails");
        assertThat(first.getDescription()).isEqualTo("Long story");
        assertThat(first.getWriter()).isEqualTo("Anh");
        assertThat(first.getResolvedDate()).isEqualTo(LocalDate.of(2026, 10, 1));
        assertThat(first.getSolution())
                .isEqualTo(new Ticket.Solution("Error 500", "Bad token", "Restart", null, "All users"));
        assertThat(saved.get(1).getWriter()).isNull();
        assertThat(saved.get(1).getResolvedDate()).isNull();
        assertThat(transactions).containsExactly("commit");
    }

    @Test
    @SuppressWarnings("unchecked")
    void recordsAnApproveDecisionByTheProjectManagerPerTicket_AC_004_2() {
        service.confirm(PM, PREVIEW);

        ArgumentCaptor<List<TicketReviewDecision>> captor = ArgumentCaptor.forClass(List.class);
        verify(decisions).saveAll(captor.capture());
        assertThat(captor.getValue())
                .extracting(TicketReviewDecision::getTicketId)
                .containsExactly(101L, 102L);
        assertThat(captor.getValue())
                .allMatch(d -> d.getReviewer().equals(PM) && d.getDecision() == ReviewDecision.APPROVE);
    }

    @Test
    void recordsOneCompletedImportBatchAndLinksTheTickets_AC_004_3() {
        service.confirm(PM, PREVIEW);

        ArgumentCaptor<ImportBatch> batch = ArgumentCaptor.forClass(ImportBatch.class);
        verify(batches).save(batch.capture());
        ImportBatch b = batch.getValue();
        assertThat(b.getStatus()).isEqualTo(ImportBatchStatus.COMPLETED);
        assertThat(b.getProjectId()).isEqualTo(PROJECT);
        assertThat(b.getPerformedBy()).isEqualTo(PM);
        assertThat(b.getSource()).isEqualTo(TicketSource.CSV);
        assertThat(b.getFileName()).isEqualTo("tickets.csv");
        assertThat(b.getPublishedCount()).isEqualTo(2);
        assertThat(b.getErrorCount()).isEqualTo(2);
        assertThat(b.getRemovedCount()).isEqualTo(1);
        assertThat(savedTickets()).allMatch(t -> t.getImportBatchId() == 7L);
    }

    @Test
    void returnsMsg32AndDeletesThePreview_AC_004_4() {
        ImportResultResponse result = service.confirm(PM, PREVIEW);

        assertThat(result.importBatchId()).isEqualTo(7L);
        assertThat(result.created()).isEqualTo(2);
        assertThat(result.skipped()).isEqualTo(3);
        assertThat(result.message()).isEqualTo(new MessageRef("MSG32", Map.of("created", 2, "skipped", 3L)));
        verify(previews).deleteById(PREVIEW);
    }

    @Test
    void rejectsPreviewWithoutValidRows_AC_004_5() {
        when(rows.findByIdPreviewIdAndStatus(PREVIEW, PreviewRowStatus.VALID)).thenReturn(List.of());

        MessageCodeException e = catchThrowableOfType(() -> service.confirm(PM, PREVIEW), MessageCodeException.class);

        assertThat(e.getStatus()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(e.getCode()).isEqualTo("MSG30");
        verify(tickets, never()).saveAll(anyCollection());
        verify(recorder, never()).recordFailed(any(), any(), anyString());
    }

    @Test
    void rejectsWhenTheProjectIsNoLongerActive_AC_004_6() {
        when(projects.isActive(PROJECT)).thenReturn(false);

        MessageCodeException e = catchThrowableOfType(() -> service.confirm(PM, PREVIEW), MessageCodeException.class);

        assertThat(e.getStatus()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(e.getCode()).isEqualTo("MSG40");
        verify(tickets, never()).saveAll(anyCollection());
        verify(recorder, never()).recordFailed(any(), any(), anyString());
    }

    @Test
    void failsWholeConfirmWhenATicketIdWasTakenMeanwhile_AC_004_7() {
        when(tickets.findExistingTicketIds(anyCollection())).thenReturn(Set.of("T-3"));

        MessageCodeException e = catchThrowableOfType(() -> service.confirm(PM, PREVIEW), MessageCodeException.class);

        assertThat(e.getStatus()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(e.getCode()).isEqualTo("MSG33");
        verify(tickets, never()).saveAll(anyCollection());
        assertThat(transactions).containsExactly("rollback");
        ArgumentCaptor<String> error = ArgumentCaptor.forClass(String.class);
        verify(recorder).recordFailed(any(ImportPreview.class), any(), error.capture());
        assertThat(error.getValue()).contains("MSG33").doesNotContain("T-3");
    }

    @Test
    void uniqueConstraintOnTicketIdIsAlsoATakenId_AC_004_7() {
        when(tickets.saveAll(anyCollection())).thenThrow(new DataIntegrityViolationException("uq_ticket_ticket_id"));

        MessageCodeException e = catchThrowableOfType(() -> service.confirm(PM, PREVIEW), MessageCodeException.class);

        assertThat(e.getStatus()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(e.getCode()).isEqualTo("MSG33");
        verify(recorder).recordFailed(any(ImportPreview.class), any(), eq("TicketIdTaken: MSG33"));
    }

    @Test
    void savingFailureImportsNothingAndRecordsAFailedBatch_AC_004_8() {
        when(tickets.saveAll(anyCollection())).thenThrow(new QueryTimeoutException("statement timeout"));

        MessageCodeException e = catchThrowableOfType(() -> service.confirm(PM, PREVIEW), MessageCodeException.class);

        assertThat(e.getStatus()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(e.getCode()).isEqualTo("MSG33");
        assertThat(transactions).containsExactly("rollback");
        ArgumentCaptor<ImportBatchRecorder.Counts> counts = ArgumentCaptor.forClass(ImportBatchRecorder.Counts.class);
        ArgumentCaptor<String> error = ArgumentCaptor.forClass(String.class);
        verify(recorder).recordFailed(any(ImportPreview.class), counts.capture(), error.capture());
        assertThat(counts.getValue()).isEqualTo(new ImportBatchRecorder.Counts(2, 1));
        assertThat(error.getValue()).isEqualTo("QueryTimeoutException: MSG33");
        verify(previews, never()).deleteById(PREVIEW);
    }

    @Test
    void failureToRecordTheFailedBatchStillAnswersMsg33_AC_004_8() {
        when(tickets.saveAll(anyCollection())).thenThrow(new QueryTimeoutException("statement timeout"));
        doThrow(new IllegalStateException("database down")).when(recorder).recordFailed(any(), any(), anyString());

        MessageCodeException e = catchThrowableOfType(() -> service.confirm(PM, PREVIEW), MessageCodeException.class);

        assertThat(e.getCode()).isEqualTo("MSG33");
    }

    @Test
    void rejectsAnotherUsersPreview_AC_004_9() {
        assertThatThrownBy(() -> service.confirm("other@company.vn", PREVIEW)).isInstanceOf(NotFoundException.class);
        verify(tickets, never()).saveAll(anyCollection());
    }

    @Test
    void publishesTheNewTicketIdsInsideTheImportTransaction_AC_004_10() {
        // published inside the transaction so @TransactionalEventListener(AFTER_COMMIT) listeners get it after the
        // commit; ImportConfirmIT proves the delivery happens after commit
        doAnswer(call -> {
                    transactions.add("event");
                    return null;
                })
                .when(events)
                .publishEvent(any(Object.class));

        service.confirm(PM, PREVIEW);

        verify(events).publishEvent(new TicketsPublishedEvent(List.of(101L, 102L)));
        assertThat(transactions).containsExactly("event", "commit");
    }

    @Test
    void rejectsCallerWhoNoLongerManagesTheProject_AC_004_11() {
        when(projects.managesActive(PM, PROJECT)).thenReturn(false);

        MessageCodeException e = catchThrowableOfType(() -> service.confirm(PM, PREVIEW), MessageCodeException.class);

        assertThat(e.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(e.getCode()).isEqualTo("MSG08");
        verify(tickets, never()).saveAll(anyCollection());
        verify(batches, never()).save(any());
        verify(recorder, never()).recordFailed(any(), any(), anyString());
        assertThat(transactions).isEmpty();
    }

    @Test
    void inactiveProjectIsCheckedBeforeManagement_AC_004_11() {
        when(projects.isActive(PROJECT)).thenReturn(false);
        when(projects.managesActive(PM, PROJECT)).thenReturn(false);

        MessageCodeException e = catchThrowableOfType(() -> service.confirm(PM, PREVIEW), MessageCodeException.class);

        assertThat(e.getCode()).isEqualTo("MSG40");
    }

    @Test
    void secondConfirmOfTheSamePreviewIsNotFoundWithoutAFailedBatch_AC_004_12() {
        // the first Confirm held the lock and deleted the preview before this one got it
        when(previews.lockByIdAndOwner(PREVIEW, PM)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.confirm(PM, PREVIEW)).isInstanceOf(NotFoundException.class);

        verify(tickets, never()).saveAll(anyCollection());
        verify(recorder, never()).recordFailed(any(), any(), anyString());
        assertThat(transactions).containsExactly("rollback");
    }

    @Test
    void validRowsAreReadAgainUnderTheLock_AC_004_12() {
        // a remove-rows that finished while this Confirm waited for the lock leaves no Valid row
        when(rows.findByIdPreviewIdAndStatus(PREVIEW, PreviewRowStatus.VALID))
                .thenReturn(List.of(row(1, "T-1", null, null)))
                .thenReturn(List.of());

        MessageCodeException e = catchThrowableOfType(() -> service.confirm(PM, PREVIEW), MessageCodeException.class);

        assertThat(e.getCode()).isEqualTo("MSG30");
        verify(tickets, never()).saveAll(anyCollection());
        verify(recorder, never()).recordFailed(any(), any(), anyString());
    }
}
