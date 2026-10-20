package vn.tsolve.ticket.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import vn.tsolve.ticket.dto.PendingReviewNotification;
import vn.tsolve.ticket.dto.ReviewQueueItem;
import vn.tsolve.ticket.dto.ReviewTicketResponse;
import vn.tsolve.ticket.entity.ReviewNotification;
import vn.tsolve.ticket.entity.ReviewTicket;

@Mapper
public interface ReviewMapper {
    @Mapping(target = "ticketId", source = "id")
    @Mapping(target = "project.projectId", source = "projectId")
    @Mapping(target = "project.name", source = "projectName")
    ReviewQueueItem toQueueItem(ReviewTicket ticket);

    @Mapping(target = "ticketId", source = "id")
    @Mapping(target = "project.projectId", source = "projectId")
    @Mapping(target = "project.name", source = "projectName")
    ReviewTicketResponse toResponse(ReviewTicket ticket);

    PendingReviewNotification toNotification(ReviewNotification notification);
}
