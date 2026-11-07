package vn.tsolve.ticket.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;
import vn.tsolve.common.exception.NotFoundException;
import vn.tsolve.ticket.dto.DraftSavedResponse;
import vn.tsolve.ticket.dto.ImportDraftSummary;
import vn.tsolve.ticket.dto.ImportPreviewResponse;
import vn.tsolve.ticket.entity.ImportPreview;
import vn.tsolve.ticket.entity.ImportPreviewRow;
import vn.tsolve.ticket.entity.ImportType;
import vn.tsolve.ticket.entity.PreviewRowStatus;
import vn.tsolve.ticket.entity.TicketSource;
import vn.tsolve.ticket.exception.ProjectAccessDeniedException;
import vn.tsolve.ticket.mapper.ImportPreviewMapperImpl;
import vn.tsolve.ticket.repository.ImportPreviewRepository;
import vn.tsolve.ticket.repository.ImportPreviewRowRepository;
import vn.tsolve.ticket.service.ProjectAccess;

class ImportDraftServiceImplTest {

    private static final String PM = "pm@company.vn";
    private static final UUID PROJECT = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final Instant NOW = Instant.parse("2026-11-07T03:00:00Z");
    private static final LocalDate FROM = LocalDate.of(2026, 11, 1);
    private static final LocalDate TO = LocalDate.of(2026, 11, 7);

    private final ImportPreviewRepository previews = mock(ImportPreviewRepository.class);
    private final ImportPreviewRowRepository rows = mock(ImportPreviewRowRepository.class);
    private final ProjectAccess projects = mock(ProjectAccess.class);

    private final ImportDraftServiceImpl service =
            new ImportDraftServiceImpl(previews, rows, projects, Clock.fixed(NOW, ZoneId.of("Asia/Ho_Chi_Minh")));

    @BeforeEach
    void setUp() {
        when(projects.managesActive(PM, PROJECT)).thenReturn(true);
        when(previews.findByIdAndOwner(anyLong(), any())).thenReturn(Optional.empty());
    }

    private ImportPreview jira(long id) {
        ImportPreview preview = ImportPreview.jira(PM, PROJECT, FROM, TO, false, NOW.minusSeconds(3600));
        ReflectionTestUtils.setField(preview, "id", id);
        when(previews.findByIdAndOwner(id, PM)).thenReturn(Optional.of(preview));
        return preview;
    }

    private ImportPreview draft(long id, Instant savedAt) {
        ImportPreview preview = jira(id);
        preview.saveAsDraft(savedAt);
        return preview;
    }

    @Test
    void saveMarksThePreviewADraftWithoutValidatingItsRows_AC_004_1() {
        ImportPreview preview = jira(42L);

        DraftSavedResponse response = service.save(PM, 42L);

        assertThat(preview.isDraft()).isTrue();
        assertThat(preview.getDraftSavedAt()).isEqualTo(NOW);
        assertThat(response.previewId()).isEqualTo(42L);
        assertThat(response.savedAt()).isEqualTo(NOW);
        assertThat(response.message().code()).isEqualTo("MSG41");
        // Needs Solution rows are fine in a Draft: no row is read, none is changed
        verify(rows, never()).findByIdPreviewIdAndStatus(anyLong(), any());
        verify(rows, never()).saveAll(any());
    }

    @Test
    void saveOfAPreviewOfAProjectNoLongerManagedIs403_AC_004_1() {
        ImportPreview preview = jira(42L);
        when(projects.managesActive(PM, PROJECT)).thenReturn(false);

        catchThrowableOfType(() -> service.save(PM, 42L), ProjectAccessDeniedException.class);

        assertThat(preview.isDraft()).isFalse();
    }

    @Test
    void listsOnlyTheCallersDraftsNewestFirstWithTheirRowCount_AC_004_2() {
        ImportPreview newer = draft(2L, NOW);
        ImportPreview older = draft(1L, NOW.minusSeconds(60));
        when(previews.findByOwnerAndDraftSavedAtIsNotNullOrderByDraftSavedAtDesc(PM))
                .thenReturn(List.of(newer, older));
        when(rows.countByIdPreviewIdAndStatusNot(2L, PreviewRowStatus.REMOVED)).thenReturn(5L);
        when(rows.countByIdPreviewIdAndStatusNot(1L, PreviewRowStatus.REMOVED)).thenReturn(3L);

        List<ImportDraftSummary> drafts = service.list(PM);

        assertThat(drafts)
                .containsExactly(
                        new ImportDraftSummary(2L, TicketSource.JIRA_IMPORT, PROJECT, 5, NOW),
                        new ImportDraftSummary(1L, TicketSource.JIRA_IMPORT, PROJECT, 3, NOW.minusSeconds(60)));
        verify(previews).findByOwnerAndDraftSavedAtIsNotNullOrderByDraftSavedAtDesc(PM);
    }

    @Test
    void reopenedDraftIsThePreviewAsSavedWithoutJira_AC_004_3() {
        ImportPreview preview = draft(42L, NOW);
        ImportPreviewRow row = ImportPreviewRow.jiraIssue(
                42L,
                1,
                new ImportPreviewRow.Values(
                        "SUP-1", "Login fails", null, null, "2026-11-03", "S", "R", "Steps", null, "All"),
                "https://acme.atlassian.net/browse/SUP-1",
                List.of());
        when(rows.findByIdPreviewIdOrderByIdRowNo(eq(42L), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(row)));
        ImportPreviewServiceImpl previewService =
                new ImportPreviewServiceImpl(previews, rows, new ImportPreviewMapperImpl(), projects);

        ImportPreviewResponse reopened = previewService.preview(PM, 42L);

        assertThat(reopened.previewId()).isEqualTo(preview.getId());
        assertThat(reopened.draftSavedAt()).isEqualTo(NOW);
        assertThat(reopened.source()).isEqualTo(TicketSource.JIRA_IMPORT);
        assertThat(reopened.resolvedFrom()).isEqualTo(FROM);
        assertThat(reopened.rows().getContent()).singleElement().satisfies(r -> assertThat(r.symptoms())
                .isEqualTo("S"));
    }

    @Test
    void deleteRemovesTheDraft_AC_004_4() {
        ImportPreview preview = draft(42L, NOW);

        service.delete(PM, 42L);

        verify(previews).delete(preview);
    }

    @Test
    void deleteOfAPreviewThatIsNotADraftIs404_AC_004_4() {
        jira(42L);

        assertThat(catchThrowableOfType(() -> service.delete(PM, 42L), NotFoundException.class)
                        .getCode())
                .isEqualTo("MSG04");
        verify(previews, never()).delete(any());
    }

    @Test
    void anotherUsersMissingOrCsvDraftIs404_AC_004_5() {
        draft(42L, NOW); // PM's own; another user asks for it
        ImportPreview csv =
                new ImportPreview(PM, PROJECT, TicketSource.CSV, ImportType.WITH_SOLUTION, "tickets.csv", NOW);
        ReflectionTestUtils.setField(csv, "id", 7L);
        when(previews.findByIdAndOwner(7L, PM)).thenReturn(Optional.of(csv));

        for (Runnable call : List.<Runnable>of(
                () -> service.delete("other@company.vn", 42L),
                () -> service.save("other@company.vn", 42L),
                () -> service.delete(PM, 99L),
                () -> service.save(PM, 99L),
                () -> service.delete(PM, 7L),
                () -> service.save(PM, 7L))) {
            assertThat(catchThrowableOfType(call::run, NotFoundException.class).getCode())
                    .isEqualTo("MSG04");
        }
        verify(previews, never()).delete(any());
        assertThat(csv.isDraft()).isFalse();
    }

    @Test
    void savingAgainUpdatesTheSameDraftAndItsTime_AC_004_7() {
        Instant firstSave = NOW.minusSeconds(86_400);
        ImportPreview preview = draft(42L, firstSave);

        DraftSavedResponse response = service.save(PM, 42L);

        assertThat(response.previewId()).isEqualTo(42L);
        assertThat(preview.getDraftSavedAt()).isEqualTo(NOW);
        verify(previews, never()).save(any());
    }
}
