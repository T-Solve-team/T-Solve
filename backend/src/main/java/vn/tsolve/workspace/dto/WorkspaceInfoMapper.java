package vn.tsolve.workspace.dto;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import vn.tsolve.workspace.entity.WorkspaceInfo;

@Mapper
public interface WorkspaceInfoMapper {
    @Mapping(target = "jiraSiteUrl", source = "jiraSiteAddress")
    WorkspaceInfoResponse toResponse(WorkspaceInfo workspace);
}
