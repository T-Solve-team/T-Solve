package vn.tsolve.analytics.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import vn.tsolve.analytics.dto.AuditEntryResponse;
import vn.tsolve.analytics.entity.AuditLogEntry;

@Mapper
public interface AuditEntryMapper {
    @Mapping(target = "actor", source = "actorEmail")
    @Mapping(target = "resourceType", source = "targetType")
    @Mapping(target = "resourceId", source = "targetId")
    @Mapping(target = "summary", expression = "java(null)")
    AuditEntryResponse toResponse(AuditLogEntry entry);
}
