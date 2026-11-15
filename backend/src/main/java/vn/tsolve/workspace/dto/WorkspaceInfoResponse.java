package vn.tsolve.workspace.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(
        name = "WorkspaceInfo",
        requiredProperties = {"companyName", "jiraSiteUrl", "serviceAccountEmail"})
public record WorkspaceInfoResponse(String companyName, String jiraSiteUrl, String serviceAccountEmail) {}
