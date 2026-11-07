package vn.tsolve.review;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import vn.tsolve.common.security.JiraAppPrincipal;
import vn.tsolve.review.controller.JiraReviewFeedbackController;
import vn.tsolve.review.service.ReviewJiraAccess;
import vn.tsolve.review.service.ReviewJiraAccess.WriterIssue;
import vn.tsolve.ticket.dto.TicketReviewView.Feedback;
import vn.tsolve.ticket.service.TicketReviewReadService;

class JiraReviewFeedbackControllerTest {

    @Test
    void missingReviewerNameReturnsNoFeedback_BUG_135() {
        var reads = mock(TicketReviewReadService.class);
        var access = mock(ReviewJiraAccess.class);
        UUID writer = UUID.randomUUID();
        when(access.inWriterIssue(any(), anyString(), any())).thenAnswer(invocation -> {
            Function<WriterIssue, Object> read = invocation.getArgument(2);
            return read.apply(new WriterIssue(writer, UUID.fromString("00000000-0000-0000-0000-00000000000a")));
        });
        when(reads.jiraFeedback(UUID.fromString("00000000-0000-0000-0000-00000000000a"), writer, "OPS-1"))
                .thenReturn(Optional.of(new Feedback(UUID.randomUUID(), "Explain the workaround")));
        var controller = new JiraReviewFeedbackController(reads, Optional.of(access));
        assertThat(controller
                        .feedback(new JiraAppPrincipal("cloud", "writer", null), "OPS-1")
                        .feedback())
                .isNull();
    }

    @Test
    void refusesToReadWithoutTrustedWorkspaceIntegration_AC_009_6() {
        var reads = mock(TicketReviewReadService.class);
        var controller = new JiraReviewFeedbackController(reads, Optional.empty());

        assertThatThrownBy(() -> controller.feedback(new JiraAppPrincipal("cloud", "writer", null), "OPS-1"))
                .isInstanceOf(AccessDeniedException.class);

        verifyNoInteractions(reads);
    }
}
