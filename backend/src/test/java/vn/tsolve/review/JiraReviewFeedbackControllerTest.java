package vn.tsolve.review;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import vn.tsolve.common.security.JiraAppPrincipal;
import vn.tsolve.review.controller.JiraReviewFeedbackController;
import vn.tsolve.ticket.service.TicketReviewReadService;

class JiraReviewFeedbackControllerTest {

    @Test
    void refusesToReadWithoutTrustedWorkspaceIntegration_AC_009_6() {
        var reads = mock(TicketReviewReadService.class);
        var controller = new JiraReviewFeedbackController(reads, Optional.empty());

        assertThatThrownBy(() -> controller.feedback(new JiraAppPrincipal("cloud", "writer", null), "OPS-1"))
                .isInstanceOf(AccessDeniedException.class);

        verifyNoInteractions(reads);
    }
}
