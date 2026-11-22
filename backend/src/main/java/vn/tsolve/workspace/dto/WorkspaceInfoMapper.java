package vn.tsolve.workspace.dto;

import java.time.Instant;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import vn.tsolve.workspace.entity.WorkspaceInfo;

@Mapper
public interface WorkspaceInfoMapper {
    @Mapping(target = "companyName", source = "workspace.companyName")
    @Mapping(target = "jiraSiteUrl", source = "workspace.jiraSiteAddress")
    WorkspaceInfoResponse toResponse(WorkspaceInfo workspace, boolean jiraAppConnected, Instant jiraAppTokenReceivedAt);
}
