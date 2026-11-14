package vn.tsolve.ticket.service;

import java.util.Optional;
import java.util.UUID;
import vn.tsolve.ticket.dto.SolutionFormSubmission;
import vn.tsolve.ticket.dto.SolutionSections;
import vn.tsolve.ticket.dto.SubmittedSolutionTicket;
import vn.tsolve.ticket.entity.TicketStatus;

/** Solution Form tickets created from the Jira App, for `ticket-review`'s Review Queue (jira-app STORY-004). */
public interface SolutionFormTicketService {

    /**
     * Creates the issue's ticket in Pending review and deletes the writer's Draft for the issue. A required section
     * must not be empty (MSG01), so an empty Solution is never stored (AC-005.5), and the five together must not
     * exceed 10,000 characters (MSG02). A second submission for the same issue answers 409 (AC-004.3). The caller has
     * checked that the writer is a member of the Project.
     */
    SubmittedSolutionTicket submit(SolutionFormSubmission submission);

    /** The status of the issue's ticket, or empty when the issue has none (AC-001.2, AC-008.2). */
    Optional<TicketStatus> findStatus(String issueKey);

    /**
     * The Solution of the writer's Solution Form ticket of the issue while it is in Changes requested, or empty when
     * the writer may not resubmit it (jira-app AC-012.1 to AC-012.3).
     */
    Optional<SolutionSections> findResubmittable(String issueKey, UUID writerId);

    /**
     * Replaces the Solution of the writer's ticket in Changes requested, puts it back into Pending review with a new
     * due date and deletes the writer's Draft (AC-012.4); the sections are checked as for {@link #submit}. 409 MSG06
     * when the ticket is no longer resubmittable (AC-012.5). The caller has checked that the writer is a member.
     */
    SubmittedSolutionTicket resubmit(SolutionFormSubmission submission);
}
