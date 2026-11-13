package vn.tsolve.ticket.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static vn.tsolve.ticket.entity.TicketStatus.CHANGES_REQUESTED;
import static vn.tsolve.ticket.entity.TicketStatus.EXPIRED;
import static vn.tsolve.ticket.entity.TicketStatus.PENDING_REVIEW;
import static vn.tsolve.ticket.entity.TicketStatus.PUBLISHED;
import static vn.tsolve.ticket.entity.TicketStatus.REJECTED;
import static vn.tsolve.ticket.entity.TicketStatus.UNPUBLISHED;

import java.time.Instant;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.test.util.ReflectionTestUtils;
import vn.tsolve.common.exception.BusinessRuleException;

class TicketStatusTest {

    private static final Set<String> ALLOWED = Set.of(
            "PENDING_REVIEW>PUBLISHED",
            "PENDING_REVIEW>REJECTED",
            "PENDING_REVIEW>CHANGES_REQUESTED",
            "PENDING_REVIEW>EXPIRED",
            "CHANGES_REQUESTED>PENDING_REVIEW",
            "CHANGES_REQUESTED>EXPIRED",
            "EXPIRED>PENDING_REVIEW",
            "PUBLISHED>UNPUBLISHED",
            "UNPUBLISHED>PUBLISHED");

    static Stream<Arguments> allPairs() {
        return Arrays.stream(TicketStatus.values())
                .flatMap(from -> Arrays.stream(TicketStatus.values()).map(to -> Arguments.of(from, to)));
    }

    @ParameterizedTest(name = "{0} -> {1}")
    @MethodSource("allPairs")
    void allowsExactlyTheTableChanges_AC_005_1(TicketStatus from, TicketStatus to) {
        assertThat(from.canMoveTo(to)).isEqualTo(ALLOWED.contains(from + ">" + to));
    }

    @Test
    void importedTicketIsPublished_AC_005_2() {
        assertThat(imported().getStatus()).isEqualTo(PUBLISHED);
    }

    @Test
    void moveToSetsStatusAndUpdatedAt_AC_005_1() {
        Ticket ticket = imported();
        Instant at = Instant.parse("2026-11-13T00:00:00Z");

        ticket.moveTo(UNPUBLISHED, at);

        assertThat(ticket.getStatus()).isEqualTo(UNPUBLISHED);
        assertThat(ReflectionTestUtils.getField(ticket, "updatedAt")).isEqualTo(at);
    }

    @Test
    void moveToAForbiddenStatusThrowsMsg06AndKeepsTheStatus_AC_005_3() {
        Ticket ticket = imported();
        ReflectionTestUtils.setField(ticket, "status", REJECTED);

        assertThatThrownBy(() -> ticket.moveTo(PENDING_REVIEW, Instant.now()))
                .isInstanceOf(BusinessRuleException.class)
                .extracting("code")
                .isEqualTo("MSG06");
        assertThat(ticket.getStatus()).isEqualTo(REJECTED);
        assertThatThrownBy(() -> imported().moveTo(CHANGES_REQUESTED, Instant.now()))
                .isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> imported().moveTo(EXPIRED, Instant.now())).isInstanceOf(BusinessRuleException.class);
    }

    static Ticket imported() {
        return Ticket.imported(
                "KAN-1",
                null,
                TicketSource.values()[0],
                null,
                "Title",
                null,
                null,
                null,
                new Ticket.Solution("s", "r", "steps", null, "scope"));
    }
}
