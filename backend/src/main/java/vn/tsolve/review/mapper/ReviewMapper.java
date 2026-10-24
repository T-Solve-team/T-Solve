package vn.tsolve.review.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import vn.tsolve.review.dto.PendingReviewNotification;
import vn.tsolve.review.dto.ReviewQueueItem;
import vn.tsolve.review.dto.ReviewTicketResponse;
import vn.tsolve.review.entity.ReviewNotification;
import vn.tsolve.review.entity.ReviewTicket;

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
