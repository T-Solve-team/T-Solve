package vn.tsolve.ticket.mapper;

import java.time.OffsetDateTime;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import vn.tsolve.ticket.dto.TicketReviewView.Detail;
import vn.tsolve.ticket.dto.TicketReviewView.Evidence;
import vn.tsolve.ticket.dto.TicketReviewView.Project;
import vn.tsolve.ticket.entity.Ticket;
import vn.tsolve.ticket.entity.TicketEvidence;

@Mapper
public interface TicketReviewMapper {

    @Mapping(target = "filename", source = "fileName")
    @Mapping(target = "sizeBytes", source = "fileSize")
    @Mapping(target = "url", ignore = true)
    Evidence evidence(TicketEvidence file);

    @Mapping(target = "ticketId", source = "ticket.id")
    @Mapping(target = "sourceTicketId", source = "ticket.ticketId")
    @Mapping(target = "description", source = "ticket.description", defaultExpression = "java(\"\")")
    @Mapping(target = "authorId", source = "ticket.writerId")
    @Mapping(target = "authorName", source = "ticket.writer")
    @Mapping(target = "reviewDueDate", source = "reviewDueDate")
    Detail detail(
            Ticket ticket,
            Project project,
            OffsetDateTime reviewDueDate,
            List<Evidence> evidence,
            String lastReviewComment,
            boolean canDecide);
}
