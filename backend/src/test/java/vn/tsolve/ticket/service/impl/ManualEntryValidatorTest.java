package vn.tsolve.ticket.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;
import vn.tsolve.ticket.dto.ManualEntryRequest;
import vn.tsolve.ticket.exception.ManualEntryRejectedException;

class ManualEntryValidatorTest {

    private static final UUID PROJECT = UUID.fromString("00000000-0000-0000-0000-000000000001");

    /** 17:30 UTC on 13 November is 00:30 on 14 November in Vietnam (edge 21). */
    private final ManualEntryValidator validator =
            new ManualEntryValidator(Clock.fixed(Instant.parse("2026-11-13T17:30:00Z"), ZoneOffset.UTC));

    /** A valid entry with the given fields replaced; a null value leaves the field out. */
    static ManualEntryRequest entry(Map<String, Object> changes) {
        Map<String, Object> fields = new HashMap<>(Map.of(
                "projectId", PROJECT.toString(),
                "ticketId", "T-1",
                "title", "Login fails",
                "description", "Users cannot sign in",
                "symptoms", "Error 500",
                "rootCause", "Bad token",
                "resolutionSteps", "Restart",
                "scope", "All users"));
        fields.putAll(changes);
        return new ObjectMapper().convertValue(fields, ManualEntryRequest.class);
    }

    private static Map<String, Object> with(String field, Object value) {
        Map<String, Object> changes = new HashMap<>();
        changes.put(field, value);
        return changes;
    }

    private ManualEntryRejectedException rejection(Map<String, Object> changes) {
        return catchThrowableOfType(() -> validator.validate(entry(changes)), ManualEntryRejectedException.class);
    }

    @Test
    void acceptsAValidEntryAndTrimsItsValues_AC_006_1() {
        Map<String, Object> changes = with("ticketId", "  T-1  ");
        changes.put("title", " Login fails ");
        changes.put("workaround", "   ");
        changes.put("resolvedDate", " 2026-11-10 ");

        ManualEntryValidator.Entry entry = validator.validate(entry(changes));

        assertThat(entry.projectId()).isEqualTo(PROJECT);
        assertThat(entry.ticketId()).isEqualTo("T-1");
        assertThat(entry.title()).isEqualTo("Login fails");
        assertThat(entry.resolvedDate()).isEqualTo(LocalDate.of(2026, 11, 10));
        assertThat(entry.solution().symptoms()).isEqualTo("Error 500");
        assertThat(entry.solution().workaround()).isNull();
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "projectId",
                "ticketId",
                "title",
                "description",
                "symptoms",
                "rootCause",
                "resolutionSteps",
                "scope"
            })
    void aMissingRequiredFieldIsMsg01NamingIt_AC_006_3(String field) {
        ManualEntryRejectedException e = rejection(with(field, null));

        assertThat(e.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(e.getCode()).isEqualTo("MSG01");
        assertThat(e.getParams()).containsEntry("field_name", field);
        assertThat(e.getField()).isEqualTo(field);
    }

    @Test
    void everyBlankFieldIsNamedInOneMsg01_AC_006_3() {
        Map<String, Object> changes = with("ticketId", "   ");
        changes.put("scope", "\t");
        changes.put("title", null);

        ManualEntryRejectedException e = rejection(changes);

        assertThat(e.getCode()).isEqualTo("MSG01");
        assertThat(e.getParams()).containsEntry("field_name", "ticketId, title, scope");
        assertThat(e.getField()).isEqualTo("ticketId");
    }

    @Test
    void workaroundAndResolvedDateAreOptional_AC_006_3() {
        Map<String, Object> changes = with("workaround", null);
        changes.put("resolvedDate", null);

        assertThat(validator.validate(entry(changes)).resolvedDate()).isNull();
    }

    @ParameterizedTest
    @CsvSource({"ticketId,50", "title,200", "description,10000"})
    void aValueOverItsLimitIsMsg02NamingTheFieldAndTheLimit_AC_006_4(String field, int max) {
        assertThatCode(() -> validator.validate(entry(with(field, "x".repeat(max)))))
                .doesNotThrowAnyException();

        ManualEntryRejectedException e = rejection(with(field, "x".repeat(max + 1)));

        assertThat(e.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(e.getCode()).isEqualTo("MSG02");
        assertThat(e.getParams()).containsEntry("field_name", field).containsEntry("max_length", max);
        assertThat(e.getField()).isEqualTo(field);
    }

    @Test
    void solutionSectionsOver10000CharactersInTotalAreMsg02_AC_006_4() {
        Map<String, Object> changes = with("symptoms", "x".repeat(5_000));
        changes.put("rootCause", "x".repeat(4_990));
        changes.put("resolutionSteps", "12345");
        changes.put("workaround", "1234");
        changes.put("scope", "1");
        assertThatCode(() -> validator.validate(entry(changes))).doesNotThrowAnyException();

        changes.put("scope", "12");
        ManualEntryRejectedException e = rejection(changes);

        assertThat(e.getCode()).isEqualTo("MSG02");
        assertThat(e.getParams()).containsEntry("field_name", "Solution").containsEntry("max_length", 10_000);
    }

    @Test
    void limitsCountCodePointsAndTrimmedText_AC_006_4() {
        // 50 emoji are 100 UTF-16 units but 50 characters (edge 20); the spaces around them do not count
        assertThatCode(() -> validator.validate(entry(with("ticketId", "  " + "😀".repeat(50) + "  "))))
                .doesNotThrowAnyException();
    }

    @ParameterizedTest
    @ValueSource(strings = {"2026-11-15", "2026-13-40", "14/11/2026", "yesterday"})
    void aFutureOrInvalidResolvedDateIsMsg39_AC_006_5(String date) {
        ManualEntryRejectedException e = rejection(with("resolvedDate", date));

        assertThat(e.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(e.getCode()).isEqualTo("MSG39");
        assertThat(e.getField()).isEqualTo("resolvedDate");
    }

    @Test
    void todayInVietnamIsAccepted_AC_006_5() {
        // still 13 November in UTC (edge 21)
        assertThat(validator.validate(entry(with("resolvedDate", "2026-11-14"))).resolvedDate())
                .isEqualTo(LocalDate.of(2026, 11, 14));
    }

    @Test
    void aMissingFieldWinsOverATooLongOneAndABadDate_AC_006_3() {
        // edge 22: the first message kind found is the answer
        Map<String, Object> changes = with("ticketId", " ");
        changes.put("title", "x".repeat(300));
        changes.put("resolvedDate", "2030-01-01");

        assertThat(rejection(changes).getCode()).isEqualTo("MSG01");

        changes.put("ticketId", "T-1");
        assertThat(rejection(changes).getCode()).isEqualTo("MSG02");
    }
}
