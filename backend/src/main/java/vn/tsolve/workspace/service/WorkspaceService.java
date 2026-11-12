package vn.tsolve.workspace.service;

import com.fasterxml.jackson.databind.JsonNode;
import vn.tsolve.workspace.dto.WorkspaceInfoResponse;

public interface WorkspaceService {
    WorkspaceInfoResponse setup(JsonNode input);

    WorkspaceInfoResponse get();
}
