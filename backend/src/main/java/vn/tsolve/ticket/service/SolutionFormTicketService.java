package vn.tsolve.ticket.service;

import java.util.Optional;
import vn.tsolve.ticket.dto.SolutionFormSubmission;
import vn.tsolve.ticket.dto.SubmittedSolutionTicket;
import vn.tsolve.ticket.entity.TicketStatus;

/** Solution Form tickets created from the Jira App, for `ticket-review`'s Review Queue (jira-app STORY-004, 006). */
public interface SolutionFormTicketService {

    /**
     * Creates the issue's ticket in Pending review and deletes the writer's Draft for the issue. Five empty sections
     * mean the form was closed and are stored as an empty Solution (AC-006.2); otherwise a required section must not
     * be empty (MSG01) and the five together must not exceed 10,000 characters (MSG02). A second submission for the
     * same issue answers 409 (AC-004.3). The caller has checked that the writer is a member of the Project.
     */
    SubmittedSolutionTicket submit(SolutionFormSubmission submission);

    /** The status of the issue's ticket, or empty when the issue has none (AC-001.2, AC-008.2). */
    Optional<TicketStatus> findStatus(String issueKey);
}
