package vn.tsolve.search.list.mapper;

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
            expression = "java(new TicketProjectRef(ticket.getProjectId(), ticket.getProjectName()))")
    @Mapping(
            target = "department",
            expression = "java(new TicketDepartmentRef(ticket.getDepartmentPublicId(), ticket.getDepartmentName()))")
    @Mapping(target = "reviewDueDate", expression = "java(null)")
    TicketSummaryResponse toResponse(SearchTicket ticket);
}
