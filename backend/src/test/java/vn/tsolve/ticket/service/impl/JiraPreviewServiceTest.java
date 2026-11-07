package vn.tsolve.ticket.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import vn.tsolve.common.exception.BusinessRuleException;
import vn.tsolve.common.exception.MessageCodeException;
import vn.tsolve.common.exception.NotFoundException;
import vn.tsolve.ticket.dto.FillSolutionRequest;
import vn.tsolve.ticket.dto.ImportPreviewResponse;
import vn.tsolve.ticket.dto.ImportPreviewRowSummary;
import vn.tsolve.ticket.entity.ImportPreview;
import vn.tsolve.ticket.entity.ImportPreviewRow;
import vn.tsolve.ticket.entity.IssueComment;
import vn.tsolve.ticket.entity.PreviewRowStatus;
import vn.tsolve.ticket.entity.TicketSource;
import vn.tsolve.ticket.mapper.ImportPreviewMapperImpl;
import vn.tsolve.ticket.repository.ImportPreviewRepository;
import vn.tsolve.ticket.repository.ImportPreviewRowRepository;
import vn.tsolve.ticket.service.ProjectAccess;

class JiraPreviewServiceTest {

    private static final String PM = "pm@company.vn";
    private static final long ID = 42L;
    private static final UUID PROJECT = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final LocalDate FROM = LocalDate.of(2026, 11, 1);
    private static final LocalDate TO = LocalDate.of(2026, 11, 6);
    private static final IssueComment COMMENT = new IssueComment("Anh", Instant.EPOCH, "Restarted the service");

    private final ImportPreviewRepository previews = mock(ImportPreviewRepository.class);
    private final ImportPreviewRowRepository rows = mock(ImportPreviewRowRepository.class);
    private final ProjectAccess projects = mock(ProjectAccess.class);
    private final ImportPreviewServiceImpl service =
            new ImportPreviewServiceImpl(previews, rows, new ImportPreviewMapperImpl(), projects);
    private ImportPreview preview;

    @BeforeEach
    void setUp() {
        preview = ImportPreview.jira(PM, PROJECT, FROM, TO, true, Instant.EPOCH);
        ReflectionTestUtils.setField(preview, "id", ID);
        when(previews.findByIdAndOwner(ID, PM)).thenReturn(Optional.of(preview));
        when(projects.managesActive(PM, PROJECT)).thenReturn(true);
    }

    private static ImportPreviewRow issue(int rowNo) {
        return ImportPreviewRow.jiraIssue(
                ID,
                rowNo,
                new ImportPreviewRow.Values(
                        "JIRA-" + rowNo,
                        "Login fails",
                        "Full description",
                        "Anh",
                        "2026-11-03",
                        null,
                        null,
                        null,
                        null,
                        null),
                "https://example.atlassian.net/browse/JIRA-" + rowNo,
                List.of(COMMENT));
    }

    private void summaryRows(List<ImportPreviewRow> content) {
        when(rows.findByIdPreviewIdOrderByIdRowNo(eq(ID), any(Pageable.class))).thenReturn(new PageImpl<>(content));
        for (PreviewRowStatus status : PreviewRowStatus.values()) {
            when(rows.countByIdPreviewIdAndStatus(ID, status)).thenAnswer(call -> content.stream()
                    .filter(r -> r.getStatus() == status)
                    .count());
        }
    }

    private void fillable(List<ImportPreviewRow> content) {
        when(previews.lockByIdAndOwner(ID, PM)).thenReturn(Optional.of(preview));
        when(rows.findAllById(any())).thenReturn(content);
        summaryRows(content);
    }

    private static FillSolutionRequest solution(List<Integer> numbers, String symptoms) {
        return new FillSolutionRequest(numbers, symptoms, " Bad token ", " Restart ", " ", " All users ");
    }

    @Test
    void fillsEachSelectedRow_AC_002_3() {
        ImportPreviewRow first = issue(1);
        ImportPreviewRow second = new ImportPreviewRow(
                ID,
                2,
                new ImportPreviewRow.Values(
                        "JIRA-2",
                        "Old title",
                        "Old description",
                        "Anh",
                        "2026-11-03",
                        "Old",
                        "Old",
                        "Old",
                        "Old",
                        "Old"),
                List.of());
        fillable(List.of(first, second));

        var response = service.fillSolution(PM, ID, solution(List.of(1, 2, 2), "  Error 500 "));

        for (ImportPreviewRow row : List.of(first, second)) {
            assertThat(row.getStatus()).isEqualTo(PreviewRowStatus.VALID);
            assertThat(row.getValues().symptoms()).isEqualTo("Error 500");
            assertThat(row.getValues().rootCause()).isEqualTo("Bad token");
            assertThat(row.getValues().resolutionSteps()).isEqualTo("Restart");
            assertThat(row.getValues().workaround()).isNull();
            assertThat(row.getValues().scope()).isEqualTo("All users");
        }
        assertThat(first.getValues().title()).isEqualTo("Login fails");
        assertThat(first.getSourceUrl()).isEqualTo("https://example.atlassian.net/browse/JIRA-1");
        assertThat(first.getComments()).containsExactly(COMMENT);
        assertThat(response.validCount()).isEqualTo(2);
        assertThat(response.needsSolutionCount()).isZero();
        assertThat(response.source()).isEqualTo(TicketSource.JIRA_IMPORT);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Iterable<ImportPreviewRow.Id>> ids = ArgumentCaptor.forClass(Iterable.class);
        verify(rows).findAllById(ids.capture());
        assertThat(ids.getValue()).containsExactly(new ImportPreviewRow.Id(ID, 1), new ImportPreviewRow.Id(ID, 2));
        verify(rows).flush();
    }

    @Test
    void keepsRowSolutionsIndependentWhenOneIsReplaced_AC_002_3() {
        ImportPreviewRow first = issue(1);
        ImportPreviewRow second = issue(2);
        fillable(List.of(first, second));
        service.fillSolution(PM, ID, solution(List.of(1, 2), "Original"));
        when(rows.findAllById(any())).thenReturn(List.of(first));

        service.fillSolution(PM, ID, new FillSolutionRequest(List.of(1), "New", "Cause", "Fix", "Temporary", "All"));

        assertThat(first.getValues().symptoms()).isEqualTo("New");
        assertThat(first.getValues().workaround()).isEqualTo("Temporary");
        assertThat(second.getValues().symptoms()).isEqualTo("Original");
        assertThat(second.getValues().rootCause()).isEqualTo("Bad token");
        assertThat(second.getValues().workaround()).isNull();
    }

    @Test
    void returnsTheSavedSolutionInFull_AC_002_2() {
        ImportPreviewRow row = issue(1);
        fillable(List.of(row));
        String symptoms = "s".repeat(2_000);
        String steps = "r".repeat(2_000);
        service.fillSolution(
                PM, ID, new FillSolutionRequest(List.of(1), symptoms, "Cause", steps, "Temporary", "All users"));
        when(rows.findById(new ImportPreviewRow.Id(ID, 1))).thenReturn(Optional.of(row));

        var response = service.row(PM, ID, 1);

        assertThat(response.symptoms()).isEqualTo(symptoms);
        assertThat(response.rootCause()).isEqualTo("Cause");
        assertThat(response.resolutionSteps()).isEqualTo(steps);
        assertThat(response.workaround()).isEqualTo("Temporary");
        assertThat(response.scope()).isEqualTo("All users");
        assertThat(response.status()).isEqualTo(PreviewRowStatus.VALID);
        assertThat(response.comments()).containsExactly(COMMENT);
        assertThat(response.sourceUrl()).isEqualTo(row.getSourceUrl());
    }

    @Test
    void rejectsCsvFillSolutionWithThePortedGate_AC_002_3() {
        ImportPreview csv = new ImportPreview(PM, PROJECT, TicketSource.CSV, "tickets.csv", Instant.EPOCH);
        when(previews.findByIdAndOwner(ID, PM)).thenReturn(Optional.of(csv));
        when(previews.lockByIdAndOwner(ID, PM)).thenReturn(Optional.of(csv));

        BusinessRuleException error = catchThrowableOfType(
                () -> service.fillSolution(PM, ID, solution(List.of(1), "Error")), BusinessRuleException.class);

        assertThat(error.getCode()).isEqualTo("MSG06");
        verifyNoInteractions(rows);
    }

    @Test
    void rejectsRemovedOrErrorSelectionsWithoutChangingAnyRow_AC_002_3() {
        ImportPreviewRow first = issue(1);
        ImportPreviewRow bad = issue(2);
        fillable(List.of(first, bad));
        for (PreviewRowStatus status : List.of(PreviewRowStatus.REMOVED, PreviewRowStatus.ERROR)) {
            ReflectionTestUtils.setField(bad, "status", status);

            BusinessRuleException error = catchThrowableOfType(
                    () -> service.fillSolution(PM, ID, solution(List.of(1, 2), "Error")), BusinessRuleException.class);

            assertThat(error.getCode()).isEqualTo("MSG06");
            assertThat(first.getStatus()).isEqualTo(PreviewRowStatus.NEEDS_SOLUTION);
            assertThat(first.getValues().symptoms()).isNull();
            assertThat(bad.getStatus()).isEqualTo(status);
        }
        verify(rows, never()).flush();
    }

    @Test
    void acceptsExactlyTenThousandTrimmedCodePoints_AC_002_5() {
        ImportPreviewRow row = issue(1);
        fillable(List.of(row));
        // The other required sections contain 25 code points; each supplementary character counts once.
        String symptoms = "\uD83D\uDE00".repeat(10_000 - 25);

        service.fillSolution(PM, ID, solution(List.of(1), "  " + symptoms + "  "));

        assertThat(row.getStatus()).isEqualTo(PreviewRowStatus.VALID);
        assertThat(row.getValues().symptoms()).isEqualTo(symptoms);
    }

    @Test
    void rejectsOverTenThousandIncludingWorkaroundWithoutChanges_AC_002_5() {
        ImportPreviewRow row = issue(1);
        fillable(List.of(row));
        FillSolutionRequest request = new FillSolutionRequest(List.of(1), "s", "r", "r", "w".repeat(9_997), "s");

        MessageCodeException error =
                catchThrowableOfType(() -> service.fillSolution(PM, ID, request), MessageCodeException.class);

        assertThat(error.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(error.getCode()).isEqualTo("MSG02");
        assertThat(error.getParams()).containsEntry("field_name", "Solution").containsEntry("max_length", 10_000);
        assertThat(row.getStatus()).isEqualTo(PreviewRowStatus.NEEDS_SOLUTION);
        assertThat(row.getValues().symptoms()).isNull();
        verifyNoInteractions(rows);
    }

    @Test
    void rejectsMissingSelectedRowsBeforeChangingAnyRow_AC_002_8() {
        ImportPreviewRow row = issue(1);
        fillable(List.of(row));

        assertThatThrownBy(() -> service.fillSolution(PM, ID, solution(List.of(1, 99), "Error")))
                .isInstanceOf(NotFoundException.class);

        assertThat(row.getStatus()).isEqualTo(PreviewRowStatus.NEEDS_SOLUTION);
        assertThat(row.getValues().symptoms()).isNull();
        verify(rows, never()).flush();
    }

    @Test
    void rejectsPreviewFinishedWhileWaitingForTheLock_AC_002_8() {
        when(previews.lockByIdAndOwner(ID, PM)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.fillSolution(PM, ID, solution(List.of(1), "Error")))
                .isInstanceOf(NotFoundException.class);
        verify(previews).findByIdAndOwner(ID, PM);
        verify(previews).lockByIdAndOwner(ID, PM);
        verifyNoInteractions(rows);
    }

    @Test
    void listsJiraSnapshotsInRowOrderWithCappedPaging_AC_002_1() {
        when(rows.findByIdPreviewIdOrderByIdRowNo(eq(ID), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(issue(21), issue(22)), PageRequest.of(1, 20), 42));

        var page = service.rows(
                PM, ID, null, PageRequest.of(1, 500, Sort.by("title").descending()));

        assertThat(page.getContent()).extracting(ImportPreviewRowSummary::rowNo).containsExactly(21, 22);
        assertThat(page.getContent())
                .extracting(ImportPreviewRowSummary::ticketId)
                .containsExactly("JIRA-21", "JIRA-22");
        assertThat(page.getContent().get(0).title()).isEqualTo("Login fails");
        assertThat(page.getContent().get(0).author()).isEqualTo("Anh");
        assertThat(page.getContent().get(0).resolvedDate()).isEqualTo("2026-11-03");
        assertThat(page.getContent().get(0).status()).isEqualTo(PreviewRowStatus.NEEDS_SOLUTION);
        assertThat(page.getMetadata().totalElements()).isEqualTo(42);
        ArgumentCaptor<Pageable> paging = ArgumentCaptor.forClass(Pageable.class);
        verify(rows).findByIdPreviewIdOrderByIdRowNo(eq(ID), paging.capture());
        assertThat(paging.getValue().getPageSize()).isEqualTo(100);
        assertThat(paging.getValue().getPageNumber()).isEqualTo(1);
        assertThat(paging.getValue().getSort().isUnsorted()).isTrue();
    }

    @Test
    void reportsJiraMetadataCountsAndMsg35_AC_002_1() {
        preview.saveAsDraft(Instant.EPOCH.plusSeconds(60));
        summaryRows(List.of(issue(1), issue(2)));

        ImportPreviewResponse response = service.preview(PM, ID);

        assertThat(response.source()).isEqualTo(TicketSource.JIRA_IMPORT);
        assertThat(response.fileName()).isNull();
        assertThat(response.projectId()).isEqualTo(PROJECT);
        assertThat(response.resolvedFrom()).isEqualTo(FROM);
        assertThat(response.resolvedTo()).isEqualTo(TO);
        assertThat(response.draftSavedAt()).isEqualTo(Instant.EPOCH.plusSeconds(60));
        assertThat(response.needsSolutionCount()).isEqualTo(2);
        assertThat(response.validCount()).isZero();
        assertThat(response.rows().getMetadata().totalElements()).isEqualTo(2);
        assertThat(response.message().code()).isEqualTo("MSG35");
        assertThat(response.message().params()).isEmpty();
    }

    @Test
    void filtersJiraRowsByNeedsSolution_AC_002_1() {
        when(rows.findByIdPreviewIdAndStatusOrderByIdRowNo(eq(ID), eq(PreviewRowStatus.NEEDS_SOLUTION), any()))
                .thenReturn(new PageImpl<>(List.of(issue(2))));

        var page = service.rows(PM, ID, PreviewRowStatus.NEEDS_SOLUTION, PageRequest.of(0, 20));

        assertThat(page.getContent())
                .extracting(ImportPreviewRowSummary::status)
                .containsOnly(PreviewRowStatus.NEEDS_SOLUTION);
    }

    @Test
    void returnsTheFullSnapshotIncludingJiraLinkAndComments_AC_002_2() {
        when(rows.findById(new ImportPreviewRow.Id(ID, 1))).thenReturn(Optional.of(issue(1)));

        var response = service.row(PM, ID, 1);

        assertThat(response.ticketId()).isEqualTo("JIRA-1");
        assertThat(response.title()).isEqualTo("Login fails");
        assertThat(response.description()).isEqualTo("Full description");
        assertThat(response.author()).isEqualTo("Anh");
        assertThat(response.resolvedDate()).isEqualTo("2026-11-03");
        assertThat(response.symptoms()).isNull();
        assertThat(response.sourceUrl()).isEqualTo("https://example.atlassian.net/browse/JIRA-1");
        assertThat(response.comments()).containsExactly(COMMENT);
    }

    @Test
    void removesJiraRowsAndUpdatesTheCounts_AC_002_7() {
        ImportPreviewRow first = issue(1);
        ImportPreviewRow second = issue(2);
        ReflectionTestUtils.setField(second, "status", PreviewRowStatus.VALID);
        ImportPreviewRow third = issue(3);
        when(rows.findAllById(any())).thenReturn(List.of(first, second));
        summaryRows(List.of(first, second, third));

        var response = service.removeRows(PM, ID, List.of(1, 2));

        assertThat(first.getStatus()).isEqualTo(PreviewRowStatus.REMOVED);
        assertThat(second.getStatus()).isEqualTo(PreviewRowStatus.REMOVED);
        assertThat(third.getStatus()).isEqualTo(PreviewRowStatus.NEEDS_SOLUTION);
        assertThat(response.removedCount()).isEqualTo(2);
        assertThat(response.needsSolutionCount()).isEqualTo(1);
        verify(rows).flush();
    }

    @Test
    void rejectsMissingAndOtherUsersJiraPreviews_AC_002_8() {
        for (Runnable call : List.<Runnable>of(
                () -> service.preview("other@company.vn", ID),
                () -> service.rows("other@company.vn", ID, null, PageRequest.of(0, 20)),
                () -> service.row("other@company.vn", ID, 1),
                () -> service.removeRows("other@company.vn", ID, List.of(1)),
                () -> service.fillSolution("other@company.vn", ID, solution(List.of(1), "Error")),
                () -> service.preview(PM, 99L),
                () -> service.rows(PM, 99L, null, PageRequest.of(0, 20)),
                () -> service.row(PM, 99L, 1),
                () -> service.removeRows(PM, 99L, List.of(1)),
                () -> service.fillSolution(PM, 99L, solution(List.of(1), "Error")))) {
            assertThatThrownBy(call::run).isInstanceOf(NotFoundException.class);
        }
        verifyNoInteractions(rows);
    }

    @Test
    void rejectsEveryJiraPreviewRequestAfterLosingProjectAccess_AC_002_9() {
        when(projects.managesActive(PM, PROJECT)).thenReturn(false);
        for (Runnable call : List.<Runnable>of(
                () -> service.preview(PM, ID),
                () -> service.rows(PM, ID, null, PageRequest.of(0, 20)),
                () -> service.row(PM, ID, 1),
                () -> service.removeRows(PM, ID, List.of(1)),
                () -> service.fillSolution(PM, ID, solution(List.of(1), "Error")))) {
            MessageCodeException error = catchThrowableOfType(call::run, MessageCodeException.class);
            assertThat(error.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
            assertThat(error.getCode()).isEqualTo("MSG08");
        }
        verifyNoInteractions(rows);
    }
}
