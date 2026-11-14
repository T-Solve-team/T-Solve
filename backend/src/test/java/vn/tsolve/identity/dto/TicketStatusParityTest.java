package vn.tsolve.identity.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import org.junit.jupiter.api.Test;

class TicketStatusParityTest {
    @Test
    void matchesTheTicketModuleStatusNames_AC_003_9() {
        var common = Arrays.stream(vn.tsolve.common.session.dto.TicketStatus.values())
                .map(Enum::name)
                .toList();
        var ticket = Arrays.stream(vn.tsolve.ticket.entity.TicketStatus.values())
                .map(Enum::name)
                .toList();

        assertThat(common).containsExactlyInAnyOrderElementsOf(ticket);
        assertThat(common)
                .containsExactlyInAnyOrder(
                        "PENDING_REVIEW", "CHANGES_REQUESTED", "PUBLISHED", "REJECTED", "UNPUBLISHED", "EXPIRED");
    }
}
