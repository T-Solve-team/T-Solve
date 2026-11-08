package vn.tsolve.ticket.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
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
import vn.tsolve.common.exception.MessageCodeException;
import vn.tsolve.common.exception.NotFoundException;
import vn.tsolve.ticket.dto.ImportResultResponse;
import vn.tsolve.ticket.dto.MessageRef;
import vn.tsolve.ticket.entity.ImportBatch;
import vn.tsolve.ticket.entity.ImportBatchStatus;
import vn.tsolve.ticket.entity.ImportPreview;
import vn.tsolve.ticket.entity.ImportPreviewRow;
import vn.tsolve.ticket.entity.ImportType;
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

/** Finish = Confirm of a Jira preview (STORY-003). JiraFinishIT covers what needs a real database. */
class JiraFinishTest {

    private static final String PM = "pm@company.vn";
    private static final long PREVIEW = 42L;
    private static final UUID PROJECT = UUID.fromString("00000000-0000-0000-0000-000000000001");

    private final ImportPreviewRepository previews = mock(ImportPreviewRepository.class);
    private final ImportPreviewRowRepository rows = mock(ImportPreviewRowRepository.class);
    private final TicketRepository tickets = mock(TicketRepository.class);
    private final TicketReviewDecisionRepository decisions = mock(TicketReviewDecisionRepository.class);
    private final ImportBatchRepository batches = mock(ImportBatchRepository.class);
    private final ImportBatchRecorder recorder = mock(ImportBatchRecorder.class);
    private final ProjectAccess projects = mock(ProjectAccess.class);
    private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
    private final List<String> transactions = new ArrayList<>();
    private ImportPreview preview;

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
            previews,
            rows,
            tickets,
            decisions,
            batches,
            recorder,
            projects,
            events,
            txManager,
            mock(vn.tsolve.common.audit.AuditService.class));

    /** A pulled issue whose Solution was filled in: a Valid row. */
    private static ImportPreviewRow issue(int rowNo, String key, String summary, String assignee) {
        return new ImportPreviewRow(
                PREVIEW,
                rowNo,
                new ImportPreviewRow.Values(
                        key,
                        summary,
                        "Login page answers 500",
                        assignee,
                        "2026-10-01",
                        "Error 500",
                        "Bad token",
                        "Restart",
                        null,
                        "All users"),
                List.of(),
                ImportType.WITH_SOLUTION);
    }

    @BeforeEach
    void setUp() {
        preview = ImportPreview.jira(
                PM, PROJECT, LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31), false, Instant.EPOCH);
        ReflectionTestUtils.setField(preview, "id", PREVIEW);
        when(previews.findByIdAndOwner(PREVIEW, PM)).thenReturn(Optional.of(preview));
        when(previews.lockByIdAndOwner(PREVIEW, PM)).thenReturn(Optional.of(preview));
        when(projects.isActive(PROJECT)).thenReturn(true);
        when(projects.managesActive(PM, PROJECT)).thenReturn(true);
        when(rows.findByIdPreviewIdAndStatus(PREVIEW, PreviewRowStatus.VALID))
                .thenReturn(List.of(
                        issue(1, "KAN-1", "Login fails", "Anh"),
                        issue(2, "KAN-2", "Token expires", null),
                        issue(4, "KAN-4", "Session lost", "Binh")));
        when(rows.countByIdPreviewIdAndStatus(PREVIEW, PreviewRowStatus.REMOVED))
                .thenReturn(1L);
        when(tickets.findExistingTicketIds(anyCollection())).thenReturn(Set.of());
        when(batches.save(any(ImportBatch.class))).thenAnswer(call -> {
            ImportBatch b = call.getArgument(0);
            ReflectionTestUtils.setField(b, "id", 7L);
            return b;
        });
        AtomicLong ids = new AtomicLong(100);
        when(tickets.saveAll(anyCollection())).thenAnswer(call -> {
            List<Ticket> saved = new ArrayList<>(call.getArgument(0));
            saved.forEach(t -> ReflectionTestUtils.setField(t, "id", new UUID(0, ids.incrementAndGet())));
            return saved;
        });
    }

    @SuppressWarnings("unchecked")
    private List<Ticket> savedTickets() {
        ArgumentCaptor<List<Ticket>> captor = ArgumentCaptor.forClass(List.class);
        verify(tickets).saveAll(captor.capture());
        return captor.getValue();
    }

    private ImportBatch savedBatch() {
        ArgumentCaptor<ImportBatch> captor = ArgumentCaptor.forClass(ImportBatch.class);
        verify(batches).save(captor.capture());
        return captor.getValue();
    }

    @Test
    void savesEachValidIssueAsAPublishedJiraTicket_AC_003_1() {
        service.confirm(PM, PREVIEW);

        List<Ticket> saved = savedTickets();
        assertThat(saved).extracting(Ticket::getTicketId).containsExactly("KAN-1", "KAN-2", "KAN-4");
        Ticket first = saved.get(0);
        assertThat(first.getProjectId()).isEqualTo(PROJECT);
        assertThat(first.getSource()).isEqualTo(TicketSource.JIRA_IMPORT);
        assertThat(first.getStatus()).isEqualTo(TicketStatus.PUBLISHED);
        assertThat(first.getTitle()).isEqualTo("Login fails");
        assertThat(first.getDescription()).isEqualTo("Login page answers 500");
        assertThat(first.getWriter()).isEqualTo("Anh");
        assertThat(first.getResolvedDate()).isEqualTo(LocalDate.of(2026, 10, 1));
        assertThat(first.getSolution())
                .isEqualTo(new Ticket.Solution("Error 500", "Bad token", "Restart", null, "All users"));
        assertThat(saved.get(1).getWriter()).isNull();
        assertThat(transactions).containsExactly("commit");
    }

    @Test
    @SuppressWarnings("unchecked")
    void recordsAnApproveDecisionByTheCallerPerTicket_AC_003_1() {
        service.confirm(PM, PREVIEW);

        ArgumentCaptor<List<TicketReviewDecision>> captor = ArgumentCaptor.forClass(List.class);
        verify(decisions).saveAll(captor.capture());
        assertThat(captor.getValue())
                .extracting(TicketReviewDecision::getTicketId)
                .containsExactly(new UUID(0, 101), new UUID(0, 102), new UUID(0, 103));
        assertThat(captor.getValue())
                .allMatch(d -> d.getReviewer().equals(PM) && d.getDecision() == ReviewDecision.APPROVE);
    }

    @Test
    void recordsOneCompletedJiraBatchAndReturnsMsg32_AC_003_1() {
        ImportResultResponse result = service.confirm(PM, PREVIEW);

        ImportBatch b = savedBatch();
        assertThat(b.getStatus()).isEqualTo(ImportBatchStatus.COMPLETED);
        assertThat(b.getSource()).isEqualTo(TicketSource.JIRA_IMPORT);
        assertThat(b.getFileName()).isNull();
        assertThat(b.getPublishedCount()).isEqualTo(3);
        assertThat(b.getErrorCount()).isZero();
        assertThat(b.getRemovedCount()).isEqualTo(1);
        assertThat(result.importBatchId()).isEqualTo(7L);
        assertThat(result.created()).isEqualTo(3);
        assertThat(result.skipped()).isZero();
        assertThat(result.message()).isEqualTo(new MessageRef("MSG32", Map.of("created", 3, "skipped", 0L)));
        assertThat(result.notice()).isNull();
        verify(previews).deleteById(PREVIEW);
    }

    @Test
    void cutsATitleLongerThan200CharactersOnTheTicket_AC_003_1() {
        // Edge 13; an emoji at the cut must not be split in half
        String summary = "a".repeat(199) + "😀" + "tail";
        when(rows.findByIdPreviewIdAndStatus(PREVIEW, PreviewRowStatus.VALID))
                .thenReturn(List.of(issue(1, "KAN-1", summary, null)));

        service.confirm(PM, PREVIEW);

        String title = savedTickets().get(0).getTitle();
        assertThat(title.codePointCount(0, title.length())).isEqualTo(200);
        assertThat(title).endsWith("😀");
    }

    @Test
    void cutsAnAssigneeLongerThan100CharactersOnTheTicket_AC_003_1_BUG_190() {
        // ticket.writer is VARCHAR(100); a Jira display name can be longer
        String assignee = "b".repeat(99) + "😀" + "tail";
        when(rows.findByIdPreviewIdAndStatus(PREVIEW, PreviewRowStatus.VALID))
                .thenReturn(List.of(issue(1, "KAN-1", "Login fails", assignee)));

        service.confirm(PM, PREVIEW);

        String writer = savedTickets().get(0).getWriter();
        assertThat(writer.codePointCount(0, writer.length())).isEqualTo(100);
        assertThat(writer).endsWith("😀");
    }

    @Test
    void aFailedBatchNamesTheRealReasonWhenItIsNotATakenTicketId_AC_003_7_BUG_190() {
        when(tickets.saveAll(anyCollection()))
                .thenThrow(new DataIntegrityViolationException(
                        "value too long", new SQLException("value too long for type character varying(100)", "22001")));

        MessageCodeException e = catchThrowableOfType(() -> service.confirm(PM, PREVIEW), MessageCodeException.class);

        assertThat(e.getCode()).isEqualTo("MSG33");
        ArgumentCaptor<String> error = ArgumentCaptor.forClass(String.class);
        verify(recorder).recordFailed(any(ImportPreview.class), any(), error.capture());
        assertThat(error.getValue()).isEqualTo("DataIntegrityViolationException: MSG33");
    }

    @Test
    void skipsIssuesThatBecameTicketsAndReturnsMsg38WithMsg32_AC_003_2() {
        when(tickets.findExistingTicketIds(anyCollection())).thenReturn(Set.of("KAN-2", "KAN-4"));

        ImportResultResponse result = service.confirm(PM, PREVIEW);

        assertThat(savedTickets()).extracting(Ticket::getTicketId).containsExactly("KAN-1");
        assertThat(result.created()).isEqualTo(1);
        assertThat(result.skipped()).isEqualTo(2);
        assertThat(result.message()).isEqualTo(new MessageRef("MSG32", Map.of("created", 1, "skipped", 2L)));
        assertThat(result.notice())
                .isEqualTo(new MessageRef("MSG38", Map.of("count", 2, "issue_keys", "KAN-2, KAN-4")));
        ImportBatch b = savedBatch();
        assertThat(b.getPublishedCount()).isEqualTo(1);
        assertThat(b.getErrorCount()).isEqualTo(2);
        verify(recorder, never()).recordFailed(any(), any(), anyString());
        assertThat(transactions).containsExactly("commit");
    }

    @Test
    void everyIssueTakenImportsNothingButRecordsACompletedBatch_AC_003_3() {
        when(tickets.findExistingTicketIds(anyCollection())).thenReturn(Set.of("KAN-1", "KAN-2", "KAN-4"));

        ImportResultResponse result = service.confirm(PM, PREVIEW);

        assertThat(result.created()).isZero();
        assertThat(result.notice())
                .isEqualTo(new MessageRef("MSG38", Map.of("count", 3, "issue_keys", "KAN-1, KAN-2, KAN-4")));
        ImportBatch b = savedBatch();
        assertThat(b.getStatus()).isEqualTo(ImportBatchStatus.COMPLETED);
        assertThat(b.getPublishedCount()).isZero();
        verify(previews).deleteById(PREVIEW);
        verify(events, never()).publishEvent(any(Object.class));
    }

    @Test
    void inactiveProjectIsRejectedWithMsg40AndKeepsThePreview_AC_003_4() {
        when(projects.isActive(PROJECT)).thenReturn(false);
        when(projects.managesActive(PM, PROJECT)).thenReturn(false);

        MessageCodeException e = catchThrowableOfType(() -> service.confirm(PM, PREVIEW), MessageCodeException.class);

        assertThat(e.getStatus()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(e.getCode()).isEqualTo("MSG40");
        verify(tickets, never()).saveAll(anyCollection());
        verify(batches, never()).save(any());
        verify(recorder, never()).recordFailed(any(), any(), anyString());
        verify(previews, never()).deleteById(any());
    }

    @Test
    void callerWhoNoLongerManagesTheProjectGets403_AC_003_5() {
        when(projects.managesActive(PM, PROJECT)).thenReturn(false);

        MessageCodeException e = catchThrowableOfType(() -> service.confirm(PM, PREVIEW), MessageCodeException.class);

        assertThat(e.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(e.getCode()).isEqualTo("MSG08");
        verify(batches, never()).save(any());
        verify(recorder, never()).recordFailed(any(), any(), anyString());
    }

    @Test
    void anIssueThatStillNeedsASolutionBlocksFinish_AC_003_6() {
        when(rows.countByIdPreviewIdAndStatus(PREVIEW, PreviewRowStatus.NEEDS_SOLUTION))
                .thenReturn(2L);

        MessageCodeException e = catchThrowableOfType(() -> service.confirm(PM, PREVIEW), MessageCodeException.class);

        assertThat(e.getStatus()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(e.getCode()).isEqualTo("MSG_NEW_4");
        assertThat(e.getParams()).isEqualTo(Map.of("count", 2L));
        verify(tickets, never()).saveAll(anyCollection());
        verify(batches, never()).save(any());
        verify(recorder, never()).recordFailed(any(), any(), anyString());
    }

    @Test
    void aPreviewWithoutAValidIssueGivesMsgNew4_AC_003_6() {
        when(rows.findByIdPreviewIdAndStatus(PREVIEW, PreviewRowStatus.VALID)).thenReturn(List.of());

        MessageCodeException e = catchThrowableOfType(() -> service.confirm(PM, PREVIEW), MessageCodeException.class);

        assertThat(e.getCode()).isEqualTo("MSG_NEW_4");
        verify(batches, never()).save(any());
    }

    @Test
    void isCheckedAgainUnderTheLock_AC_003_6() {
        // a remove-rows that finished while this Finish waited for the lock leaves no Valid row
        when(rows.findByIdPreviewIdAndStatus(PREVIEW, PreviewRowStatus.VALID))
                .thenReturn(List.of(issue(1, "KAN-1", "Login fails", null)))
                .thenReturn(List.of());

        MessageCodeException e = catchThrowableOfType(() -> service.confirm(PM, PREVIEW), MessageCodeException.class);

        assertThat(e.getCode()).isEqualTo("MSG_NEW_4");
        verify(recorder, never()).recordFailed(any(), any(), anyString());
    }

    @Test
    void savingFailureImportsNothingAndRecordsAFailedJiraBatch_AC_003_7() {
        when(tickets.saveAll(anyCollection())).thenThrow(new QueryTimeoutException("statement timeout"));

        MessageCodeException e = catchThrowableOfType(() -> service.confirm(PM, PREVIEW), MessageCodeException.class);

        assertThat(e.getStatus()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(e.getCode()).isEqualTo("MSG33");
        assertThat(transactions).containsExactly("rollback");
        ArgumentCaptor<String> error = ArgumentCaptor.forClass(String.class);
        verify(recorder).recordFailed(any(ImportPreview.class), any(), error.capture());
        assertThat(error.getValue()).isEqualTo("QueryTimeoutException: MSG33");
        verify(previews, never()).deleteById(any());
    }

    @Test
    void aUniqueViolationIsRetriedOnceWithoutTheTakenKeys_AC_003_2() {
        // Edge 6: another Finish or the Solution Form made KAN-2 a Ticket after the pre-check
        when(tickets.findExistingTicketIds(anyCollection()))
                .thenReturn(Set.of())
                .thenReturn(Set.of("KAN-2"));
        when(tickets.saveAll(anyCollection()))
                .thenThrow(new DataIntegrityViolationException("uq_ticket_ticket_id"))
                .thenAnswer(call -> new ArrayList<>(call.getArgument(0)));

        ImportResultResponse result = service.confirm(PM, PREVIEW);

        assertThat(result.created()).isEqualTo(2);
        assertThat(result.notice()).isEqualTo(new MessageRef("MSG38", Map.of("count", 1, "issue_keys", "KAN-2")));
        assertThat(transactions).containsExactly("rollback", "commit");
        verify(recorder, never()).recordFailed(any(), any(), anyString());
    }

    @Test
    void aSecondUniqueViolationGivesMsg33_AC_003_7() {
        when(tickets.saveAll(anyCollection())).thenThrow(new DataIntegrityViolationException("uq_ticket_ticket_id"));

        MessageCodeException e = catchThrowableOfType(() -> service.confirm(PM, PREVIEW), MessageCodeException.class);

        assertThat(e.getCode()).isEqualTo("MSG33");
        verify(tickets, times(2)).saveAll(anyCollection());
        assertThat(transactions).containsExactly("rollback", "rollback");
        verify(recorder).recordFailed(any(ImportPreview.class), any(), anyString());
    }

    @Test
    void aSecondFinishOfTheSamePreviewIsNotFound_AC_003_8() {
        // the first Finish held the lock and deleted the preview before this one got it
        when(previews.lockByIdAndOwner(PREVIEW, PM)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.confirm(PM, PREVIEW)).isInstanceOf(NotFoundException.class);

        verify(batches, never()).save(any());
        verify(recorder, never()).recordFailed(any(), any(), anyString());
    }

    @Test
    void publishesTheNewTicketIdsInsideTheTransaction_AC_003_9() {
        doAnswer(call -> {
                    transactions.add("event");
                    return null;
                })
                .when(events)
                .publishEvent(any(Object.class));
        when(tickets.findExistingTicketIds(anyCollection())).thenReturn(Set.of("KAN-2"));

        service.confirm(PM, PREVIEW);

        verify(events).publishEvent(new TicketsPublishedEvent(List.of(new UUID(0, 101), new UUID(0, 102))));
        assertThat(transactions).containsExactly("event", "commit");
    }

    @Test
    void finishingADraftDeletesIt_AC_003_10() {
        preview.saveAsDraft(Instant.EPOCH);

        service.confirm(PM, PREVIEW);

        verify(previews).deleteById(PREVIEW);
    }
}
