package vn.tsolve.ticket.service;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import vn.tsolve.ticket.dto.TicketReviewView.Detail;
import vn.tsolve.ticket.dto.TicketReviewView.Feedback;
import vn.tsolve.ticket.dto.TicketReviewView.ProjectAccess;
import vn.tsolve.ticket.dto.TicketReviewView.QueueItem;

public interface TicketReviewReadService {

    Page<QueueItem> queue(Map<Long, ProjectAccess> projects, UUID departmentId, Pageable page, List<String> sort);

    List<UUID> eligibleIds(Map<Long, ProjectAccess> projects, UUID departmentId);

    Detail detail(Map<Long, ProjectAccess> projects, UUID accountId, boolean manager, UUID ticketId);

    /** Current change request for the verified writer and issue in an already bound Workspace. */
    Optional<Feedback> jiraFeedback(long projectId, UUID writerId, String issueKey);
}
