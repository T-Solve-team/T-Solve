package vn.tsolve.ticket.csv;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import vn.tsolve.ticket.entity.ImportPreviewRow;

class CsvRowValidatorTest {

    // 2026-10-22 01:00 in Vietnam is still 2026-10-21 in UTC
    private static final Clock CLOCK =
            Clock.fixed(Instant.parse("2026-10-21T18:00:00Z"), ZoneId.of("Asia/Ho_Chi_Minh"));

    private final CsvRowValidator validator = new CsvRowValidator(CLOCK);

    private static ImportPreviewRow.Values valid(String ticketId) {
        return new ImportPreviewRow.Values(
                ticketId, "Login fails", null, null, null, "Error 500", "Bad token", "Restart", null, "All users");
    }

    private static ParsedCsv.Row row(int rowNo, ImportPreviewRow.Values values) {
        return new ParsedCsv.Row(rowNo, values);
    }

    private List<RowError> errorsOf(ImportPreviewRow.Values values) {
        return validator.validate(List.of(row(1, values)), Set.of()).get(0).errors();
    }

    private static String repeat(String s, int times) {
        return s.repeat(times);
    }

    @Test
    void validRowHasNoErrors_AC_002_1() {
        assertThat(errorsOf(valid("T-1"))).isEmpty();
    }

    @Test
    void listsEveryFailedRuleOfARow_AC_002_1() {
        var v = new ImportPreviewRow.Values(
                "T-1", " ", null, null, "21/10/2026", "", "Bad token", "Restart", null, "All users");

        assertThat(errorsOf(v))
                .extracting(RowError::code, RowError::field)
                .containsExactly(tuple("MSG01", "title"), tuple("MSG01", "symptoms"), tuple("MSG39", "resolved_date"));
    }

    @Test
    void blankOrMissingRequiredValueIsMsg01_AC_002_2() {
        var v = new ImportPreviewRow.Values(null, "  ", null, null, null, "\t", null, "", null, " \n ");

        assertThat(errorsOf(v))
                .allMatch(e -> e.code().equals("MSG01"))
                .extracting(RowError::field)
                .containsExactly("ticket_id", "title", "symptoms", "root_cause", "resolution_steps", "scope");
        assertThat(errorsOf(v).get(0).params()).isEqualTo(Map.of("field_name", "ticket_id"));
    }

    @Test
    void workaroundDescriptionAuthorAndDateAreOptional_AC_002_2() {
        assertThat(errorsOf(valid("T-1"))).isEmpty();
    }

    @Test
    void fieldOverItsLimitIsMsg02_AC_002_3() {
        var v = new ImportPreviewRow.Values(
                repeat("x", 51),
                repeat("x", 201),
                repeat("x", 10_001),
                repeat("x", 101),
                null,
                "a",
                "b",
                "c",
                null,
                "d");

        List<RowError> errors = errorsOf(v);

        assertThat(errors).extracting(RowError::field).containsExactly("ticket_id", "title", "description", "author");
        assertThat(errors.get(0).params()).isEqualTo(Map.of("field_name", "ticket_id", "max_length", 50));
    }

    @Test
    void fieldExactlyAtItsLimitIsValid_AC_002_3() {
        var v = new ImportPreviewRow.Values(
                repeat("x", 50),
                repeat("x", 200),
                repeat("x", 10_000),
                repeat("x", 100),
                null,
                "a",
                "b",
                "c",
                null,
                "d");

        assertThat(errorsOf(v)).isEmpty();
    }

    @Test
    void lengthCountsCharactersNotUtf16Units_AC_002_3() {
        // 200 emoji = 400 UTF-16 units but 200 characters
        var v = new ImportPreviewRow.Values(
                "T-1", repeat("😀", 200), null, "Nguyễn Văn Anh", null, "a", "b", "c", null, "d");

        assertThat(errorsOf(v)).isEmpty();
    }

    @Test
    void solutionOverTenThousandCharactersInTotalIsMsg02_AC_002_4() {
        var over = new ImportPreviewRow.Values(
                "T-1",
                "t",
                null,
                null,
                null,
                repeat("a", 4_000),
                repeat("b", 3_000),
                repeat("c", 2_000),
                "w",
                repeat("d", 1_000));
        var exact = new ImportPreviewRow.Values(
                "T-1",
                "t",
                null,
                null,
                null,
                repeat("a", 4_000),
                repeat("b", 3_000),
                repeat("c", 2_000),
                null,
                repeat("d", 1_000));

        assertThat(errorsOf(over))
                .containsExactly(new RowError("MSG02", null, Map.of("field_name", "Solution", "max_length", 10_000)));
        assertThat(errorsOf(exact)).isEmpty();
    }

    @Test
    void existingTicketIdIsMsg31_AC_002_5() {
        List<CsvRowValidator.Result> results =
                validator.validate(List.of(row(1, valid("T-1")), row(2, valid("T-2"))), Set.of("T-2"));

        assertThat(results.get(0).errors()).isEmpty();
        assertThat(results.get(1).errors())
                .containsExactly(new RowError("MSG31", "ticket_id", Map.of("ticket_id", "T-2")));
    }

    @Test
    void everyRowSharingATicketIdIsMsg78_AC_002_6() {
        List<CsvRowValidator.Result> results = validator.validate(
                List.of(row(1, valid("T-1")), row(2, valid("T-2")), row(3, valid("T-1")), row(4, valid("T-1"))),
                Set.of());

        assertThat(results).extracting(r -> r.errors().isEmpty()).containsExactly(false, true, false, false);
        assertThat(results.get(0).errors())
                .containsExactly(new RowError("MSG78", "ticket_id", Map.of("ticket_id", "T-1")));
    }

    @Test
    void duplicateInFileThatAlsoExistsGetsBothErrors_AC_002_6() {
        List<CsvRowValidator.Result> results =
                validator.validate(List.of(row(1, valid("T-1")), row(2, valid("T-1"))), Set.of("T-1"));

        assertThat(results.get(1).errors()).extracting(RowError::code).containsExactly("MSG31", "MSG78");
    }

    @Test
    void invalidOrFutureResolvedDateIsMsg39_AC_002_7() {
        for (String date : List.of("2026-02-30", "21/10/2026", "2026-10-23", "yesterday")) {
            var v = new ImportPreviewRow.Values("T-1", "t", null, null, date, "a", "b", "c", null, "d");
            assertThat(errorsOf(v)).as(date).containsExactly(new RowError("MSG39", "resolved_date", Map.of()));
        }
    }

    @Test
    void todayInVietnamIsNotInTheFuture_AC_002_7() {
        // UTC is still on 2026-10-21, Vietnam is already on 2026-10-22
        var v = new ImportPreviewRow.Values("T-1", "t", null, null, "2026-10-22", "a", "b", "c", null, "d");

        assertThat(errorsOf(v)).isEmpty();
    }

    @Test
    void ticketIdsAreComparedAfterTrimming_AC_002_10() {
        List<CsvRowValidator.Result> results = validator.validate(
                List.of(row(1, valid(" T-1")), row(2, valid("T-1 ")), row(3, valid("t-1"))), Set.of());

        assertThat(results).extracting(r -> r.errors().size()).containsExactly(1, 1, 0);
    }

    @Test
    void ticketIdsOfReturnsTrimmedNonBlankIds_AC_002_10() {
        assertThat(CsvRowValidator.ticketIdsOf(
                        List.of(row(1, valid(" T-1 ")), row(2, valid("")), row(3, valid(null)), row(4, valid("T-1")))))
                .containsExactly("T-1");
    }
}
