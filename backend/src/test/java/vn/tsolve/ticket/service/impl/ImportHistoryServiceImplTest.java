package vn.tsolve.ticket.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import vn.tsolve.common.exception.MessageCodeException;
import vn.tsolve.common.security.Role;
import vn.tsolve.common.security.TSolveUser;
import vn.tsolve.ticket.dto.ImportHistoryItem;
import vn.tsolve.ticket.dto.ImportHistoryQuery;
import vn.tsolve.ticket.entity.ImportBatch;
import vn.tsolve.ticket.entity.ImportPreview;
import vn.tsolve.ticket.repository.ImportBatchRepository;
import vn.tsolve.ticket.repository.ImportBatchRepository.HistoryRow;

class ImportHistoryServiceImplTest {

    private static final TSolveUser PM =
            new TSolveUser(UUID.randomUUID(), "pm@example.test", "An", Set.of(Role.PROJECT_MANAGER));
    private static final Clock CLOCK =
            Clock.fixed(Instant.parse("2026-11-14T00:00:00Z"), ZoneId.of("Asia/Ho_Chi_Minh"));
    private final ImportBatchRepository batches = mock(ImportBatchRepository.class);
    private final ImportHistoryServiceImpl service = new ImportHistoryServiceImpl(batches, CLOCK);

    private ImportHistoryItem item(String performer, String error) {
        HistoryRow row = mock(HistoryRow.class);
        when(row.getPerformedBy()).thenReturn(performer);
        when(row.getError()).thenReturn(error);
        when(row.getSource()).thenReturn("CSV");
        when(row.getStatus()).thenReturn("COMPLETED");
        when(batches.findHistory(eq(PM.id()), eq(null), eq(null), eq(null), eq(null), any()))
                .thenReturn(new PageImpl<>(List.of(row)));
        return service.list(PM, new ImportHistoryQuery(null, null, null, null, null, null))
                .getContent()
                .getFirst();
    }

    @ParameterizedTest
    @ValueSource(strings = {"Nguyễn Văn An", "stub-user", "22222222-2222-2222-2222-222222222222"})
    @DisplayName("AC-007.3 returns the resolved account name or unchanged raw performer")
    void performedBy_AC_007_3(String performer) {
        assertThat(item(performer, null).performedBy()).isEqualTo(performer);
    }

    @ParameterizedTest
    @CsvSource({
        "JiraUnavailableException MSG50 details MSG33,MSG50",
        "ImportFailedException MSG33,MSG33",
        "failure MSG_NEW_4 context,MSG_NEW_4"
    })
    @DisplayName("AC-007.4 returns only the first MSG code")
    void messageCode_AC_007_4(String error, String code) {
        assertThat(item("An", error).errorCode()).isEqualTo(code);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"database unavailable without a message code"})
    @DisplayName("AC-007.4 an error without an MSG code returns no error code")
    void noMessageCode_AC_007_4(String error) {
        assertThat(item("An", error).errorCode()).isNull();
    }

    @Test
    @DisplayName("AC-007.5 date bounds include both calendar days in Vietnam")
    void vietnamBounds_AC_007_5() {
        Instant start = Instant.parse("2026-11-13T17:00:00Z");
        Instant end = Instant.parse("2026-11-14T17:00:00Z");
        when(batches.findHistory(PM.id(), null, null, start, end, PageRequest.of(0, 20)))
                .thenReturn(new PageImpl<>(List.of()));
        service.list(
                PM,
                new ImportHistoryQuery(null, null, LocalDate.of(2026, 11, 14), LocalDate.of(2026, 11, 14), null, null));
        verify(batches).findHistory(PM.id(), null, null, start, end, PageRequest.of(0, 20));
    }

    @Test
    @DisplayName("AC-007.6 rejects a reversed date range with 400 MSG39 before querying")
    void reversedDates_AC_007_6() {
        MessageCodeException error = catchThrowableOfType(
                () -> service.list(
                        PM,
                        new ImportHistoryQuery(
                                null, null, LocalDate.of(2026, 11, 15), LocalDate.of(2026, 11, 14), null, null)),
                MessageCodeException.class);
        assertThat(error.getCode()).isEqualTo("MSG39");
        assertThat(error.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
        verifyNoInteractions(batches);
    }

    @ParameterizedTest
    @CsvSource({"-1,20", "0,0", "0,101"})
    @DisplayName("AC-007.1 rejects pages below zero and sizes outside 1 to 100")
    void invalidPaging_AC_007_1(int page, int size) {
        MessageCodeException error = catchThrowableOfType(
                () -> service.list(PM, new ImportHistoryQuery(null, null, null, null, page, size)),
                MessageCodeException.class);
        assertThat(error.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
        verifyNoInteractions(batches);
    }

    @Test
    @DisplayName("AC-007.8 failed Jira Finish carries the preview's resolved range")
    void recordsFailedFinishRange_AC_007_8() {
        ImportBatchRepository batches = mock(ImportBatchRepository.class);
        LocalDate from = LocalDate.of(2026, 10, 1);
        LocalDate to = LocalDate.of(2026, 10, 31);
        ImportPreview preview = ImportPreview.jira("pm-a", UUID.randomUUID(), from, to, false, Instant.now());

        new ImportBatchRecorder(batches).recordFailed(preview, new ImportBatchRecorder.Counts(0, 1), "MSG33");

        ArgumentCaptor<ImportBatch> saved = ArgumentCaptor.forClass(ImportBatch.class);
        verify(batches).save(saved.capture());
        assertThat(saved.getValue()).extracting("dateFrom", "dateTo").containsExactly(from, to);
    }
}
