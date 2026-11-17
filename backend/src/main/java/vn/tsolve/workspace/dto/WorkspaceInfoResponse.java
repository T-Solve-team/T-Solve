package vn.tsolve.workspace.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

@Schema(
        name = "WorkspaceInfo",
        requiredProperties = {"companyName", "jiraSiteUrl", "jiraAppConnected", "jiraAppTokenReceivedAt"})
public record WorkspaceInfoResponse(
        String companyName,
        String jiraSiteUrl,
        boolean jiraAppConnected,

        @Schema(nullable = true, description = "When the Jira App's token arrived; null while not connected.")
        Instant jiraAppTokenReceivedAt) {}
