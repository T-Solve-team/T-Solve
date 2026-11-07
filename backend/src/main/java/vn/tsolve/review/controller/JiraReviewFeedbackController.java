package vn.tsolve.review.controller;

import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.Map;
import java.util.Optional;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.tsolve.common.security.JiraAppPrincipal;
import vn.tsolve.common.security.Roles;
import vn.tsolve.review.service.ReviewJiraAccess;
import vn.tsolve.ticket.service.TicketReviewReadService;

@RestController
@RequestMapping("/api/v1/jira-app/issues/{issueKey}/review-feedback")
public class JiraReviewFeedbackController {

    private final TicketReviewReadService reads;
    private final Optional<ReviewJiraAccess> jiraAccess;

    public JiraReviewFeedbackController(TicketReviewReadService reads, Optional<ReviewJiraAccess> jiraAccess) {
        this.reads = reads;
        this.jiraAccess = jiraAccess;
    }

    @GetMapping
    @Operation(operationId = "getJiraReviewFeedback")
    @PreAuthorize("hasRole('" + Roles.JIRA_APP + "')")
    public FeedbackResponse feedback(
            @AuthenticationPrincipal JiraAppPrincipal caller,
            @PathVariable @Size(max = 50) @Pattern(regexp = "^[A-Z][A-Z0-9_]*-[1-9][0-9]*$") String issueKey) {
        if (caller == null || caller.accountId() == null || caller.accountId().isBlank()) {
            throw new AccessDeniedException("Verified Jira user required");
        }
        ReviewJiraAccess access =
                jiraAccess.orElseThrow(() -> new AccessDeniedException("Verified Jira access required"));
        return access.inWriterIssue(
                caller,
                issueKey,
                writer -> new FeedbackResponse(reads.jiraFeedback(writer.projectId(), writer.accountId(), issueKey)
                        .map(feedback -> new Message(
                                "MSG64",
                                Map.of(
                                        "manager_name",
                                        access.accountName(feedback.reviewerId())
                                                .filter(name -> !name.isBlank())
                                                .orElseThrow(
                                                        () -> new IllegalStateException("Reviewer name unavailable")),
                                        "comment",
                                        feedback.comment())))
                        .orElse(null)));
    }

    public record FeedbackResponse(Message feedback) {}

    public record Message(String code, Map<String, String> params) {}
}
