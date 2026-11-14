package vn.tsolve.ticket.service.impl;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.tsolve.ticket.dto.SolutionFormSubmission;
import vn.tsolve.ticket.dto.SolutionSections;
import vn.tsolve.ticket.dto.SubmittedSolutionTicket;
import vn.tsolve.ticket.entity.TicketStatus;
import vn.tsolve.ticket.exception.ProjectNotActiveException;
import vn.tsolve.ticket.exception.SolutionSectionRequiredException;
import vn.tsolve.ticket.exception.SolutionTooLongException;
import vn.tsolve.ticket.exception.TicketAlreadyExistsException;
import vn.tsolve.ticket.repository.SolutionDraftRepository;
import vn.tsolve.ticket.repository.TicketRepository;
import vn.tsolve.ticket.service.SolutionFormTicketService;

@Service
public class SolutionFormTicketServiceImpl implements SolutionFormTicketService {

    /** The five sections together (jira-app AC-002.4, AC-002.5). */
    static final int MAX_SOLUTION_LENGTH = 10_000;

    /** The length of `ticket.title`. */
    static final int MAX_TITLE_LENGTH = 200;

    private static final ZoneId REVIEW_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    private final TicketRepository tickets;
    private final SolutionDraftRepository drafts;
    private final Clock clock;

    public SolutionFormTicketServiceImpl(TicketRepository tickets, SolutionDraftRepository drafts, Clock clock) {
        this.tickets = tickets;
        this.drafts = drafts;
        this.clock = clock;
    }

    @Override
    @Transactional
    public SubmittedSolutionTicket submit(SolutionFormSubmission submission) {
        SolutionSections sections = submission.sections();
        // Every submission is checked, so an empty Solution never reaches the Review Queue (AC-005.5).
        requireSections(sections);
        LocalDate today = LocalDate.ofInstant(clock.instant(), REVIEW_ZONE);
        UUID id = tickets.insertSolutionFormTicket(
                        submission.projectId(),
                        submission.issueKey(),
                        title(submission.title(), submission.issueKey()),
                        submission.writerName(),
                        submission.writerId(),
                        sections.symptoms(),
                        sections.rootCause(),
                        sections.resolutionSteps(),
                        sections.workaround().isBlank() ? null : sections.workaround(),
                        sections.scopeOfApplicability(),
                        today)
                .orElseThrow(() -> notCreated(submission.issueKey()));
        drafts.deleteDraft(submission.writerId(), submission.projectId(), submission.issueKey());
        return new SubmittedSolutionTicket(id, submission.issueKey(), TicketStatus.PENDING_REVIEW);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<TicketStatus> findStatus(String issueKey) {
        return tickets.findStatusByTicketId(issueKey);
    }

    // The order of the form, so MSG01 is shown under the first empty section (AC-002.3).
    private static void requireSections(SolutionSections sections) {
        required(sections.symptoms(), "Symptoms", "symptoms");
        required(sections.rootCause(), "Root cause", "rootCause");
        required(sections.resolutionSteps(), "Resolution steps", "resolutionSteps");
        required(sections.scopeOfApplicability(), "Scope of applicability", "scopeOfApplicability");
        if (sections.length() > MAX_SOLUTION_LENGTH) {
            throw new SolutionTooLongException(MAX_SOLUTION_LENGTH);
        }
    }

    private static void required(String text, String fieldName, String field) {
        if (text.isBlank()) {
            throw new SolutionSectionRequiredException(fieldName, field);
        }
    }

    // An issue without a summary still gets a title; a long one is cut to the column.
    private static String title(String summary, String issueKey) {
        String title = summary == null || summary.isBlank() ? issueKey : summary.strip();
        if (title.codePointCount(0, title.length()) <= MAX_TITLE_LENGTH) {
            return title;
        }
        return title.substring(0, title.offsetByCodePoints(0, MAX_TITLE_LENGTH));
    }

    // No row inserted: either the Ticket ID exists already, or the Project stopped being ACTIVE meanwhile.
    private RuntimeException notCreated(String issueKey) {
        return tickets.findStatusByTicketId(issueKey).isPresent()
                ? new TicketAlreadyExistsException(issueKey)
                : new ProjectNotActiveException();
    }
}
