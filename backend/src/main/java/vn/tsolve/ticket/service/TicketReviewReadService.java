package vn.tsolve.ticket.service;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import vn.tsolve.ticket.dto.TicketReviewView.Detail;
import vn.tsolve.ticket.dto.TicketReviewView.ExpiredItem;
import vn.tsolve.ticket.dto.TicketReviewView.Feedback;
import vn.tsolve.ticket.dto.TicketReviewView.HistoryFilter;
import vn.tsolve.ticket.dto.TicketReviewView.HistoryItem;
import vn.tsolve.ticket.dto.TicketReviewView.ProjectAccess;
import vn.tsolve.ticket.dto.TicketReviewView.QueueItem;
import vn.tsolve.ticket.dto.TicketReviewView.ReviewText;

public interface TicketReviewReadService {

    Page<QueueItem> queue(Map<UUID, ProjectAccess> projects, UUID departmentId, Pageable page, List<String> sort);

    List<UUID> eligibleIds(Map<UUID, ProjectAccess> projects, UUID departmentId);

    Page<ExpiredItem> expired(
            Map<UUID, ProjectAccess> projects, UUID departmentId, int page, int size, List<String> sort);

    List<UUID> expiredIds(Map<UUID, ProjectAccess> projects, UUID departmentId);

    Detail detail(Map<UUID, ProjectAccess> projects, UUID accountId, boolean manager, UUID ticketId);

    /**
     * The text of a Pending review Solution Form ticket of a managed Project, for AI check (STORY-015). Refuses with
     * the decisions' errors: 404 MSG04 missing, 403 MSG08 unmanaged, 409 MSG06 not Pending review or not Solution Form.
     */
    ReviewText reviewText(Set<UUID> managedProjectIds, UUID ticketId);

    /** Stored review decisions on the managed Projects, filtered and paged (STORY-011). */
    Page<HistoryItem> history(Map<UUID, ProjectAccess> projects, HistoryFilter filter);

    /** Current change request for the verified writer and issue in an already bound Workspace. */
    Optional<Feedback> jiraFeedback(UUID projectId, UUID writerId, String issueKey);

    /** Latest Reject reason for the verified writer's Rejected ticket of this issue (AC-009.13). */
    Optional<Feedback> jiraRejection(UUID projectId, UUID writerId, String issueKey);
}
