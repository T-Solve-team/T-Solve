package vn.tsolve.common.exception;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;
import vn.tsolve.knowledge.SolutionRepository;
import vn.tsolve.knowledge.SolutionService;
import vn.tsolve.ticket.ResolvedTicketRepository;
import vn.tsolve.ticket.TicketService;

class LegacyNotFoundTest {
    @Test
    void usesNoDataCodeForBothLegacyResources_BUG_035() {
        var tickets = new TicketService(mock(ResolvedTicketRepository.class));
        var solutions = new SolutionService(mock(SolutionRepository.class));
        assertThatThrownBy(() -> tickets.get(123L))
                .isInstanceOfSatisfying(NotFoundException.class, error -> assertThat(error.getCode())
                        .isEqualTo("MSG04"));
        assertThatThrownBy(() -> solutions.get(123L))
                .isInstanceOfSatisfying(NotFoundException.class, error -> assertThat(error.getCode())
                        .isEqualTo("MSG04"));
    }
}
