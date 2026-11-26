package vn.tsolve.search.list.mapper;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import vn.tsolve.search.list.dto.TicketSummaryResponse;
import vn.tsolve.search.scope.dto.TicketDepartmentRef;
import vn.tsolve.search.scope.dto.TicketProjectRef;
import vn.tsolve.search.scope.entity.SearchTicket;

@Mapper(imports = {TicketProjectRef.class, TicketDepartmentRef.class})
public interface TicketSummaryMapper {
    @Mapping(target = "ticketId", source = "publicId")
    @Mapping(
            target = "project",
            expression = "java(new TicketProjectRef(ticket.getProjectPublicId(), ticket.getProjectName()))")
    @Mapping(
            target = "department",
            expression = "java(new TicketDepartmentRef(ticket.getDepartmentPublicId(), ticket.getDepartmentName()))")
    @Mapping(target = "reviewDueDate", expression = "java(startOfDay(ticket.getReviewDueDate()))")
    TicketSummaryResponse toResponse(SearchTicket ticket);

    /** The due date as its first moment in Vietnam time, as the Review Queue shows it (AC-006.7). */
    default Instant startOfDay(LocalDate date) {
        return date == null
                ? null
                : date.atStartOfDay(ZoneId.of("Asia/Ho_Chi_Minh")).toInstant();
    }
}
