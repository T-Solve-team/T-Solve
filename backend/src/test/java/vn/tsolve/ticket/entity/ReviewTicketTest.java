package vn.tsolve.ticket.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import vn.tsolve.ticket.dto.ReviewAction;
import vn.tsolve.ticket.dto.ReviewRegistration;
import vn.tsolve.ticket.dto.ReviewSource;
import vn.tsolve.ticket.dto.ReviewStatus;
import vn.tsolve.ticket.dto.SolutionSections;
import vn.tsolve.ticket.service.ReviewException;

class ReviewTicketTest {
    private static final Instant ENTERED = Instant.parse("2026-10-20T09:00:00Z");

    @Test
    void publishesPendingTicket_AC_002_1() {
        ReviewTicket ticket = ticket();
        ticket.decide(ReviewAction.APPROVE, null, ENTERED.plusSeconds(60));
        assertThat(ticket.getStatus()).isEqualTo(ReviewStatus.PUBLISHED);
    }

    @Test
    void rejectsFinally_AC_003_2_AC_003_4() {
        ReviewTicket ticket = ticket();
        ticket.decide(ReviewAction.REJECT, "Incorrect resolution", ENTERED);
        assertThat(ticket.getStatus()).isEqualTo(ReviewStatus.REJECTED);
        assertThatThrownBy(() -> ticket.decide(ReviewAction.APPROVE, null, ENTERED))
                .isInstanceOf(ReviewException.class);
    }

    @Test
    void requiresNonBlankReasonAndComment_AC_003_3_AC_004_3() {
        for (ReviewAction action : List.of(ReviewAction.REJECT, ReviewAction.REQUEST_CHANGES)) {
            for (String invalid : List.of("", "   ", "x".repeat(1001))) {
                assertThatThrownBy(() -> ticket().decide(action, invalid, ENTERED))
                        .isInstanceOf(ReviewException.class);
            }
        }
    }

    @Test
    void requestsChangesOnSingleTicket_AC_004_2() {
        ReviewTicket ticket = ticket();
        ticket.decide(ReviewAction.REQUEST_CHANGES, "Add reproduction steps", ENTERED);
        assertThat(ticket.getStatus()).isEqualTo(ReviewStatus.CHANGES_REQUESTED);
    }

    static ReviewTicket ticket() {
        return new ReviewTicket(new ReviewRegistration(
                "IT-1",
                "VPN fails",
                10L,
                "IT Support",
                ReviewSource.SOLUTION_FORM,
                "writer-1",
                "Writer",
                new SolutionSections("VPN fails", "Expired certificate", "Renew certificate", null, "VPN clients"),
                List.of(),
                ENTERED,
                ENTERED.plusSeconds(3 * 86400)));
    }
}
