package vn.tsolve.ticket.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PagedModel;
import org.springframework.test.util.ReflectionTestUtils;
import vn.tsolve.common.error.NotFoundException;
import vn.tsolve.ticket.csv.RowError;
import vn.tsolve.ticket.dto.ImportPreviewResponse;
import vn.tsolve.ticket.dto.ImportPreviewRowResponse;
import vn.tsolve.ticket.dto.ImportPreviewRowSummary;
import vn.tsolve.ticket.dto.MessageRef;
import vn.tsolve.ticket.entity.ImportPreview;
import vn.tsolve.ticket.entity.ImportPreviewRow;
import vn.tsolve.ticket.entity.PreviewRowStatus;
import vn.tsolve.ticket.entity.TicketSource;
import vn.tsolve.ticket.mapper.ImportPreviewMapperImpl;
import vn.tsolve.ticket.repository.ImportPreviewRepository;
import vn.tsolve.ticket.repository.ImportPreviewRowRepository;

class ImportPreviewServiceImplTest {

    private static final String PM = "pm@company.vn";
    private static final long ID = 42L;

    private final ImportPreviewRepository previews = mock(ImportPreviewRepository.class);
    private final ImportPreviewRowRepository rows = mock(ImportPreviewRowRepository.class);
    private final ImportPreviewServiceImpl service =
            new ImportPreviewServiceImpl(previews, rows, new ImportPreviewMapperImpl());

    private static ImportPreviewRow row(int rowNo, String symptoms, List<RowError> errors) {
        return new ImportPreviewRow(
                ID,
                rowNo,
                new ImportPreviewRow.Values(
                        "T-" + rowNo, "Login fails", "Long story", "Anh", null, symptoms, "b", "c", null, "d"),
                errors);
    }

    @BeforeEach
    void setUp() {
        ImportPreview preview = new ImportPreview(PM, 1L, TicketSource.CSV, "tickets.csv", Instant.EPOCH);
        ReflectionTestUtils.setField(preview, "id", ID);
        when(previews.findByIdAndOwner(eq(ID), any()))
                .thenAnswer(call -> PM.equals(call.getArgument(1)) ? Optional.of(preview) : Optional.empty());
    }

    @Test
    void listsRowsPageByPageWithSnippets_AC_002_8() {
        when(rows.findByIdPreviewIdOrderByIdRowNo(eq(ID), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(row(1, "x".repeat(300), List.of())), PageRequest.of(0, 20), 41));

        PagedModel<ImportPreviewRowSummary> page = service.rows(PM, ID, null, PageRequest.of(0, 20));

        assertThat(page.getContent()).hasSize(1);
        assertThat(page.getContent().get(0).symptoms()).hasSize(200);
        assertThat(page.getMetadata().totalElements()).isEqualTo(41);
    }

    @Test
    void capsThePageSizeAtOneHundred_AC_002_8() {
        when(rows.findByIdPreviewIdOrderByIdRowNo(eq(ID), any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));

        service.rows(PM, ID, null, PageRequest.of(2, 500));

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(rows).findByIdPreviewIdOrderByIdRowNo(eq(ID), pageable.capture());
        assertThat(pageable.getValue().getPageSize()).isEqualTo(100);
        assertThat(pageable.getValue().getPageNumber()).isEqualTo(2);
    }

    @Test
    void filtersRowsByResult_AC_002_11() {
        when(rows.findByIdPreviewIdAndStatusOrderByIdRowNo(eq(ID), eq(PreviewRowStatus.ERROR), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(row(3, "a", List.of(new RowError("MSG01", "title", Map.of()))))));

        PagedModel<ImportPreviewRowSummary> page = service.rows(PM, ID, PreviewRowStatus.ERROR, PageRequest.of(0, 20));

        assertThat(page.getContent())
                .extracting(ImportPreviewRowSummary::status)
                .containsOnly(PreviewRowStatus.ERROR);
    }

    @Test
    void removesValidAndErrorRows_AC_003_1() {
        ImportPreviewRow valid = row(1, "a", List.of());
        ImportPreviewRow error = row(2, "a", List.of(new RowError("MSG01", "title", Map.of())));
        when(rows.findAllById(any())).thenReturn(List.of(valid, error));
        when(rows.countByIdPreviewIdAndStatus(ID, PreviewRowStatus.VALID)).thenReturn(3L);
        when(rows.countByIdPreviewIdAndStatus(ID, PreviewRowStatus.ERROR)).thenReturn(1L);
        when(rows.countByIdPreviewIdAndStatus(ID, PreviewRowStatus.REMOVED)).thenReturn(2L);
        when(rows.findByIdPreviewIdOrderByIdRowNo(eq(ID), any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));

        ImportPreviewResponse response = service.removeRows(PM, ID, List.of(1, 2, 99));

        assertThat(valid.getStatus()).isEqualTo(PreviewRowStatus.REMOVED);
        assertThat(error.getStatus()).isEqualTo(PreviewRowStatus.REMOVED);
        assertThat(response.validCount()).isEqualTo(3);
        assertThat(response.errorCount()).isEqualTo(1);
        assertThat(response.removedCount()).isEqualTo(2);
        assertThat(response.message()).isEqualTo(new MessageRef("MSG30", Map.of("count", 1L)));
    }

    @Test
    void rejectsAnotherUsersPreview_AC_003_2() {
        assertThatThrownBy(() -> service.removeRows("other@company.vn", ID, List.of(1)))
                .isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> service.rows("other@company.vn", ID, null, PageRequest.of(0, 20)))
                .isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> service.row("other@company.vn", ID, 1)).isInstanceOf(NotFoundException.class);
        verifyNoInteractions(rows);
    }

    @Test
    void rejectsAPreviewThatNoLongerExists_AC_003_2() {
        when(previews.findByIdAndOwner(anyLong(), anyString())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.removeRows(PM, 7L, List.of(1))).isInstanceOf(NotFoundException.class);
    }

    @Test
    void rejectsARowNumberNotInThePreview_AC_003_2() {
        when(rows.findById(new ImportPreviewRow.Id(ID, 99))).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.row(PM, ID, 99)).isInstanceOf(NotFoundException.class);
    }

    @Test
    void returnsOneRowInFull_AC_003_3() {
        String longSymptoms = "x".repeat(5_000);
        when(rows.findById(new ImportPreviewRow.Id(ID, 1))).thenReturn(Optional.of(row(1, longSymptoms, List.of())));

        ImportPreviewRowResponse row = service.row(PM, ID, 1);

        assertThat(row.rowNo()).isEqualTo(1);
        assertThat(row.symptoms()).isEqualTo(longSymptoms);
        assertThat(row.description()).isEqualTo("Long story");
        assertThat(row.status()).isEqualTo(PreviewRowStatus.VALID);
    }
}
