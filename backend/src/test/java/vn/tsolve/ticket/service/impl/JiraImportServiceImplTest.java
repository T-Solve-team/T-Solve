package vn.tsolve.ticket.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;
import vn.tsolve.common.exception.MessageCodeException;
import vn.tsolve.common.exception.NotFoundException;
import vn.tsolve.ticket.dto.ImportPreviewResponse;
import vn.tsolve.ticket.entity.ImportPreview;
import vn.tsolve.ticket.entity.ImportPreviewRow;
import vn.tsolve.ticket.entity.IssueComment;
import vn.tsolve.ticket.entity.PreviewRowStatus;
import vn.tsolve.ticket.entity.TicketSource;
import vn.tsolve.ticket.exception.JiraUnavailableException;
import vn.tsolve.ticket.mapper.ImportPreviewMapperImpl;
import vn.tsolve.ticket.repository.ImportPreviewRepository;
import vn.tsolve.ticket.repository.ImportPreviewRowRepository;
import vn.tsolve.ticket.repository.TicketRepository;
import vn.tsolve.ticket.service.JiraIssue;
import vn.tsolve.ticket.service.JiraIssuePage;
import vn.tsolve.ticket.service.JiraIssueSource;
import vn.tsolve.ticket.service.ProjectAccess;

class JiraImportServiceImplTest {

    private static final String PM = "pm@company.vn";
    private static final UUID PROJECT = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final String KEY = "SUP";
    // 2026-11-07 10:00 in Vietnam
    private static final Clock CLOCK =
            Clock.fixed(Instant.parse("2026-11-07T03:00:00Z"), ZoneId.of("Asia/Ho_Chi_Minh"));
    private static final LocalDate FROM = LocalDate.of(2026, 11, 1);
    private static final LocalDate TO = LocalDate.of(2026, 11, 7);

    private final JiraIssueSource jira = mock(JiraIssueSource.class);
    private final ImportPreviewRepository previews = mock(ImportPreviewRepository.class);
    private final ImportPreviewRowRepository rows = mock(ImportPreviewRowRepository.class);
    private final TicketRepository tickets = mock(TicketRepository.class);
    private final ProjectAccess projects = mock(ProjectAccess.class);
    private final ImportBatchRecorder recorder = mock(ImportBatchRecorder.class);

    private final PlatformTransactionManager txManager = new PlatformTransactionManager() {
        @Override
        public TransactionStatus getTransaction(TransactionDefinition definition) {
            return new SimpleTransactionStatus();
        }

        @Override
        public void commit(TransactionStatus status) {}

        @Override
        public void rollback(TransactionStatus status) {}
    };

    private final JiraImportServiceImpl service = new JiraImportServiceImpl(
            jira, previews, rows, tickets, projects, recorder, new ImportPreviewMapperImpl(), CLOCK, txManager);

    @BeforeEach
    void setUp() {
        when(projects.managesActive(PM, PROJECT)).thenReturn(true);
        when(projects.jiraProjectKey(PROJECT)).thenReturn(Optional.of(KEY));
        when(tickets.findExistingTicketIds(anyCollection())).thenReturn(Set.of());
        when(previews.save(any(ImportPreview.class))).thenAnswer(call -> {
            ImportPreview p = call.getArgument(0);
            ReflectionTestUtils.setField(p, "id", 42L);
            return p;
        });
    }

    private static JiraIssue issue(int n) {
        return new JiraIssue(
                KEY + "-" + n,
                "Summary " + n,
                "Description " + n,
                "Lan Nguyen",
                Instant.parse("2026-11-02T17:30:00Z"), // 2026-11-03 00:30 in Vietnam
                "https://acme.atlassian.net/browse/" + KEY + "-" + n,
                List.of(new IssueComment("Minh", Instant.parse("2026-11-02T10:00:00Z"), "Restarted the pod")));
    }

    private static List<JiraIssue> issues(int first, int last) {
        return IntStream.rangeClosed(first, last)
                .mapToObj(JiraImportServiceImplTest::issue)
                .toList();
    }

    @SuppressWarnings("unchecked")
    private List<ImportPreviewRow> savedRows() {
        ArgumentCaptor<List<ImportPreviewRow>> saved = ArgumentCaptor.forClass(List.class);
        verify(rows).saveAll(saved.capture());
        return saved.getValue();
    }

    @Test
    void keepsIssuesWithoutTicketOldestFirstAsNeedsSolutionRows_AC_001_1() {
        when(jira.resolvedIssues(eq(KEY), eq(FROM), eq(TO), isNull(), any()))
                .thenReturn(new JiraIssuePage(issues(1, 3), "p2"));
        when(jira.resolvedIssues(eq(KEY), eq(FROM), eq(TO), eq("p2"), any()))
                .thenReturn(new JiraIssuePage(issues(4, 5), null));
        when(tickets.findExistingTicketIds(List.of("SUP-1", "SUP-2", "SUP-3"))).thenReturn(Set.of("SUP-2"));

        ImportPreviewResponse response = service.pull(PM, PROJECT, FROM, TO);

        List<ImportPreviewRow> saved = savedRows();
        assertThat(saved).extracting(r -> r.getValues().ticketId()).containsExactly("SUP-1", "SUP-3", "SUP-4", "SUP-5");
        assertThat(saved).extracting(ImportPreviewRow::getRowNo).containsExactly(1, 2, 3, 4);
        assertThat(saved).extracting(ImportPreviewRow::getStatus).containsOnly(PreviewRowStatus.NEEDS_SOLUTION);
        assertThat(response.source()).isEqualTo(TicketSource.JIRA_IMPORT);
        assertThat(response.fileName()).isNull();
        assertThat(response.resolvedFrom()).isEqualTo(FROM);
        assertThat(response.resolvedTo()).isEqualTo(TO);
        assertThat(response.needsSolutionCount()).isEqualTo(4);
        assertThat(response.message()).isNull();
        assertThat(response.rows().getContent()).hasSize(4);
    }

    @Test
    void storesASnapshotOfEachIssue_AC_001_2() {
        when(jira.resolvedIssues(eq(KEY), eq(FROM), eq(TO), isNull(), any()))
                .thenReturn(new JiraIssuePage(issues(1, 1), null));

        service.pull(PM, PROJECT, FROM, TO);

        ImportPreviewRow row = savedRows().get(0);
        ImportPreviewRow.Values values = row.getValues();
        assertThat(values.ticketId()).isEqualTo("SUP-1");
        assertThat(values.title()).isEqualTo("Summary 1");
        assertThat(values.description()).isEqualTo("Description 1");
        assertThat(values.author()).isEqualTo("Lan Nguyen");
        assertThat(values.resolvedDate()).isEqualTo("2026-11-03"); // Vietnam date, not the UTC one
        assertThat(values.symptoms()).isNull();
        assertThat(row.getSourceUrl()).isEqualTo("https://acme.atlassian.net/browse/SUP-1");
        assertThat(row.getComments()).singleElement().satisfies(c -> {
            assertThat(c.author()).isEqualTo("Minh");
            assertThat(c.body()).isEqualTo("Restarted the pod");
        });
        verify(jira, times(1)).resolvedIssues(anyString(), any(), any(), any(), any());
    }

    @Test
    void keepsAtMost100AndReturnsMsg35WhenMoreMatch_AC_001_3() {
        when(jira.resolvedIssues(eq(KEY), eq(FROM), eq(TO), isNull(), any()))
                .thenReturn(new JiraIssuePage(issues(1, 100), "p2"));
        when(jira.resolvedIssues(eq(KEY), eq(FROM), eq(TO), eq("p2"), any()))
                .thenReturn(new JiraIssuePage(issues(101, 101), null));

        ImportPreviewResponse response = service.pull(PM, PROJECT, FROM, TO);

        assertThat(savedRows()).hasSize(100);
        assertThat(response.message().code()).isEqualTo("MSG35");
        ArgumentCaptor<ImportPreview> preview = ArgumentCaptor.forClass(ImportPreview.class);
        verify(previews).save(preview.capture());
        assertThat(preview.getValue().isMoreAvailable()).isTrue();
    }

    @Test
    void exactly100IssuesIsNotMsg35_AC_001_3() {
        when(jira.resolvedIssues(eq(KEY), eq(FROM), eq(TO), isNull(), any()))
                .thenReturn(new JiraIssuePage(issues(1, 100), null));

        assertThat(service.pull(PM, PROJECT, FROM, TO).message()).isNull();
    }

    @Test
    void stopsAtTheScanCapAndReportsMoreAvailable_AC_001_3() {
        // every issue is already a Ticket except one per page: the scan stops at 20 pages (Edge 3)
        when(jira.resolvedIssues(eq(KEY), eq(FROM), eq(TO), any(), any())).thenAnswer(call -> {
            String token = call.getArgument(3);
            int page = token == null ? 0 : Integer.parseInt(token);
            return new JiraIssuePage(issues(page * 10 + 1, page * 10 + 10), String.valueOf(page + 1));
        });
        when(tickets.findExistingTicketIds(anyCollection())).thenAnswer(call -> {
            List<String> keys = new ArrayList<>(call.<java.util.Collection<String>>getArgument(0));
            keys.remove(0);
            return Set.copyOf(keys);
        });

        ImportPreviewResponse response = service.pull(PM, PROJECT, FROM, TO);

        verify(jira, times(JiraImportServiceImpl.MAX_PAGES)).resolvedIssues(anyString(), any(), any(), any(), any());
        assertThat(savedRows()).hasSize(JiraImportServiceImpl.MAX_PAGES);
        assertThat(response.message().code()).isEqualTo("MSG35");
    }

    @Test
    void missingDateIsMsg01WithoutCallingJira_AC_001_4() {
        assertCode(() -> service.pull(PM, PROJECT, null, TO), HttpStatus.BAD_REQUEST, "MSG01");
        assertCode(() -> service.pull(PM, PROJECT, FROM, null), HttpStatus.BAD_REQUEST, "MSG01");
        verifyNoInteractions(jira, previews, recorder);
    }

    @Test
    void reversedOrFutureRangeIsMsg39WithoutCallingJira_AC_001_5() {
        assertCode(() -> service.pull(PM, PROJECT, TO, FROM), HttpStatus.BAD_REQUEST, "MSG39");
        assertCode(() -> service.pull(PM, PROJECT, FROM, TO.plusDays(1)), HttpStatus.BAD_REQUEST, "MSG39");
        assertCode(() -> service.pull(PM, PROJECT, TO.plusDays(1), TO.plusDays(2)), HttpStatus.BAD_REQUEST, "MSG39");
        verifyNoInteractions(jira, previews, recorder);
    }

    @Test
    void todayIsAValidEndDate_AC_001_5() {
        when(jira.resolvedIssues(eq(KEY), eq(TO), eq(TO), isNull(), any()))
                .thenReturn(new JiraIssuePage(issues(1, 1), null));

        assertThat(service.pull(PM, PROJECT, TO, TO).needsSolutionCount()).isEqualTo(1);
    }

    @Test
    void noIssueWithoutTicketIsMsg04AndNoPreview_AC_001_6() {
        when(jira.resolvedIssues(eq(KEY), eq(FROM), eq(TO), isNull(), any()))
                .thenReturn(new JiraIssuePage(issues(1, 2), null));
        when(tickets.findExistingTicketIds(anyCollection())).thenReturn(Set.of("SUP-1", "SUP-2"));

        NotFoundException e = catchThrowableOfType(() -> service.pull(PM, PROJECT, FROM, TO), NotFoundException.class);

        assertThat(e.getCode()).isEqualTo("MSG04");
        verify(previews, never()).deleteByOwner(anyString());
        verify(previews, never()).save(any());
    }

    @Test
    void jiraFailureIsMsg50WithAFailedBatchAndNoPreview_AC_001_7() {
        when(jira.resolvedIssues(eq(KEY), eq(FROM), eq(TO), isNull(), any()))
                .thenThrow(new JiraUnavailableException("HTTP 401"));

        assertCode(() -> service.pull(PM, PROJECT, FROM, TO), HttpStatus.BAD_GATEWAY, "MSG50");

        verify(recorder).recordJiraFailed(eq(PROJECT), eq(PM), anyString());
        verify(previews, never()).deleteByOwner(anyString());
        verify(previews, never()).save(any());
    }

    @Test
    void givesEveryJiraPageOfOnePullTheSame60SecondDeadline_AC_001_13_BUG_189() {
        when(jira.resolvedIssues(eq(KEY), eq(FROM), eq(TO), isNull(), any()))
                .thenReturn(new JiraIssuePage(issues(1, 1), "p2"));
        when(jira.resolvedIssues(eq(KEY), eq(FROM), eq(TO), eq("p2"), any()))
                .thenReturn(new JiraIssuePage(issues(2, 2), null));

        service.pull(PM, PROJECT, FROM, TO);

        Instant deadline = Instant.parse("2026-11-07T03:01:00Z"); // CLOCK + 60 s
        verify(jira).resolvedIssues(KEY, FROM, TO, null, deadline);
        verify(jira).resolvedIssues(KEY, FROM, TO, "p2", deadline);
    }

    @Test
    void aFailedBatchThatCannotBeRecordedStillAnswersMsg50_AC_001_7_BUG_192() {
        when(jira.resolvedIssues(eq(KEY), eq(FROM), eq(TO), isNull(), any()))
                .thenThrow(new JiraUnavailableException("HTTP 401"));
        doThrow(new DataAccessResourceFailureException("database down"))
                .when(recorder)
                .recordJiraFailed(any(), anyString(), anyString());

        assertCode(() -> service.pull(PM, PROJECT, FROM, TO), HttpStatus.BAD_GATEWAY, "MSG50");
    }

    @Test
    void aParallelPullOfTheSameUserIsSavedAgainAndWins_AC_001_10_BUG_193() {
        when(jira.resolvedIssues(eq(KEY), eq(FROM), eq(TO), isNull(), any()))
                .thenReturn(new JiraIssuePage(issues(1, 1), null));
        // the other pull's open preview takes uq_import_preview_owner_open first
        when(previews.save(any(ImportPreview.class)))
                .thenThrow(new DataIntegrityViolationException("uq_import_preview_owner_open"))
                .thenAnswer(call -> {
                    ImportPreview p = call.getArgument(0);
                    ReflectionTestUtils.setField(p, "id", 42L);
                    return p;
                });

        assertThat(service.pull(PM, PROJECT, FROM, TO).needsSolutionCount()).isEqualTo(1);
        verify(previews, times(2)).deleteByOwner(PM);
        verify(jira, times(1)).resolvedIssues(anyString(), any(), any(), any(), any());
    }

    @Test
    void projectWithoutJiraKeyIsMsg50WithoutCallingJira_AC_001_7() {
        when(projects.jiraProjectKey(PROJECT)).thenReturn(Optional.empty());

        assertCode(() -> service.pull(PM, PROJECT, FROM, TO), HttpStatus.BAD_GATEWAY, "MSG50");

        verifyNoInteractions(jira);
        verify(recorder).recordJiraFailed(eq(PROJECT), eq(PM), anyString());
        verify(previews, never()).save(any());
    }

    @Test
    void projectNotManagedOrNotActiveIs403WithoutCallingJira_AC_001_8() {
        when(projects.managesActive(PM, PROJECT)).thenReturn(false);

        assertCode(() -> service.pull(PM, PROJECT, FROM, TO), HttpStatus.FORBIDDEN, "MSG08");

        verifyNoInteractions(jira, previews, recorder);
    }

    @Test
    void replacesThePreviousOpenPreviewOnlyAfterJira_AC_001_10() {
        when(jira.resolvedIssues(eq(KEY), eq(FROM), eq(TO), isNull(), any()))
                .thenReturn(new JiraIssuePage(issues(1, 1), null));

        service.pull(PM, PROJECT, FROM, TO);

        InOrder order = inOrder(jira, previews);
        order.verify(jira).resolvedIssues(eq(KEY), eq(FROM), eq(TO), isNull(), any());
        order.verify(previews).deleteByOwner(PM);
        order.verify(previews).save(any(ImportPreview.class));
    }

    private static void assertCode(Runnable call, HttpStatus status, String code) {
        MessageCodeException e = catchThrowableOfType(call::run, MessageCodeException.class);
        assertThat(e).as(code).isNotNull();
        assertThat(e.getStatus()).isEqualTo(status);
        assertThat(e.getCode()).isEqualTo(code);
    }
}
