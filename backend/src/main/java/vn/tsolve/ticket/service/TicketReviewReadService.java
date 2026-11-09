package vn.tsolve.ticket.service;

import java.util.List;
import java.util.Map;
import java.util.Optional;
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

public interface TicketReviewReadService {

    Page<QueueItem> queue(Map<UUID, ProjectAccess> projects, UUID departmentId, Pageable page, List<String> sort);

    List<UUID> eligibleIds(Map<UUID, ProjectAccess> projects, UUID departmentId);

    Page<ExpiredItem> expired(
            Map<UUID, ProjectAccess> projects, UUID departmentId, int page, int size, List<String> sort);

    List<UUID> expiredIds(Map<UUID, ProjectAccess> projects, UUID departmentId);

    Detail detail(Map<UUID, ProjectAccess> projects, UUID accountId, boolean manager, UUID ticketId);

    /** Stored review decisions on the managed Projects, filtered and paged (STORY-011). */
    Page<HistoryItem> history(Map<UUID, ProjectAccess> projects, HistoryFilter filter);

    /** Current change request for the verified writer and issue in an already bound Workspace. */
    Optional<Feedback> jiraFeedback(UUID projectId, UUID writerId, String issueKey);
}
