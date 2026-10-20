package vn.tsolve.ticket.manual;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.tsolve.ticket.dto.ReviewRegistration;
import vn.tsolve.ticket.dto.ReviewSource;
import vn.tsolve.ticket.dto.ReviewTicketResponse;
import vn.tsolve.ticket.dto.SolutionSections;
import vn.tsolve.ticket.service.ReviewService;

@RestController
@Profile("manual-review")
@RequestMapping("/manual-review")
@Tag(name = "manual-review-tools", description = "Local test-classpath tools; absent from the production jar")
public class ManualReviewToolsController {
    private final ReviewService review;
    private final JdbcTemplate jdbc;
    private volatile Fixtures current;

    public ManualReviewToolsController(ReviewService review, JdbcTemplate jdbc) {
        this.review = review;
        this.jdbc = jdbc;
    }

    @GetMapping("/csrf")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Load the CSRF cookie before trying POST requests in Swagger")
    public Map<String, String> csrf(@Parameter(hidden = true) CsrfToken token) {
        return Map.of("headerName", token.getHeaderName(), "token", token.getToken());
    }

    @GetMapping("/fixtures")
    @PreAuthorize("hasRole('MANUAL_REVIEW_OPERATOR')")
    @Operation(summary = "IDs for the current manual-test batch")
    public Fixtures fixtures() {
        return current;
    }

    @PostMapping("/fixtures")
    @PreAuthorize("hasRole('MANUAL_REVIEW_OPERATOR')")
    @Operation(summary = "Create a fresh batch; existing decisions and audit history are preserved")
    public Fixtures createFixtures() {
        return seedFixtures();
    }

    Fixtures seedFixtures() {
        String batch = UUID.randomUUID().toString().substring(0, 8);
        current = new Fixtures(
                batch,
                submit(batch, "APPROVE", 10L, false, ReviewSource.SOLUTION_FORM).ticketId(),
                submit(batch, "REJECT", 10L, false, ReviewSource.SOLUTION_FORM).ticketId(),
                submit(batch, "CHANGES", 10L, false, ReviewSource.SOLUTION_FORM).ticketId(),
                submit(batch, "EMPTY", 10L, true, ReviewSource.SOLUTION_FORM).ticketId(),
                submit(batch, "FOREIGN", 20L, false, ReviewSource.SOLUTION_FORM).ticketId(),
                submit(batch, "IMPORTED", 10L, false, ReviewSource.MANUAL).ticketId());
        return current;
    }

    @GetMapping("/state/{ticketId}")
    @PreAuthorize("hasRole('MANUAL_REVIEW_OPERATOR')")
    @Operation(summary = "Inspect actual database status, decision, audit and pending email handoff")
    public Map<String, Object> state(@PathVariable long ticketId) {
        return Map.of(
                "ticket",
                        jdbc.queryForMap(
                                "select id, source_ticket_id, project_id, status, review_due_at, review_comment from ticket_review where id=?",
                                ticketId),
                "decisions",
                        jdbc.queryForList(
                                "select id, action, actor_id, writer_id, comment, decided_at from ticket_review_decision where ticket_id=? order by id",
                                ticketId),
                "audit",
                        jdbc.queryForList(
                                "select actor, action, resource, result from audit_event where resource=? order by id",
                                "ticket:" + ticketId),
                "notifications",
                        jdbc.queryForList(
                                "select n.id, n.recipient_id, n.action, n.comment, n.delivered_at from ticket_review_notification n join ticket_review_decision d on d.id=n.decision_id where d.ticket_id=? order by n.id",
                                ticketId));
    }

    private ReviewTicketResponse submit(
            String batch, String label, long projectId, boolean empty, ReviewSource source) {
        Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        SolutionSections solution = empty
                ? new SolutionSections(null, null, null, null, null)
                : new SolutionSections(
                        "VPN connection fails",
                        "Expired certificate",
                        "Renew certificate and reconnect",
                        null,
                        "Managed VPN clients");
        return review.registerSubmission(new ReviewRegistration(
                "MANUAL-" + batch + "-" + label,
                "Manual test " + label,
                projectId,
                projectId == 10L ? "Manual IT" : "Manual HR",
                source,
                "APPROVE".equals(label) ? "pm" : "writer-1",
                "Manual Writer",
                solution,
                List.of(),
                now,
                now.plus(3, ChronoUnit.DAYS)));
    }

    public record Fixtures(
            String batch,
            long approveId,
            long rejectId,
            long changesId,
            long emptySolutionId,
            long foreignTicketId,
            long importedTicketId) {}
}
