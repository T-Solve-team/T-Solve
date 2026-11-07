package vn.tsolve.search.query.mapper;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import vn.tsolve.search.query.dto.TicketSearchResultResponse;
import vn.tsolve.search.query.repository.TicketRankingRepository.RankedRow;
import vn.tsolve.search.query.service.impl.Excerpts;
import vn.tsolve.search.scope.dto.TicketDepartmentRef;
import vn.tsolve.search.scope.dto.TicketProjectRef;

@Mapper(imports = {TicketProjectRef.class, TicketDepartmentRef.class, Excerpts.class})
public interface TicketSearchResultMapper {
    @Mapping(target = "project", expression = "java(new TicketProjectRef(row.getProjectId(), row.getProjectName()))")
    @Mapping(
            target = "department",
            expression = "java(new TicketDepartmentRef(row.getDepartmentId(), row.getDepartmentName()))")
    @Mapping(target = "excerpt", expression = "java(Excerpts.of(row.getSymptoms(), row.getResolutionSteps()))")
    @Mapping(target = "resolvedAt", source = "resolvedDate")
    TicketSearchResultResponse toResponse(RankedRow row);

    default OffsetDateTime resolvedAt(LocalDate date) {
        return date == null
                ? null
                : date.atStartOfDay(ZoneId.of("Asia/Ho_Chi_Minh")).toOffsetDateTime();
    }
}
