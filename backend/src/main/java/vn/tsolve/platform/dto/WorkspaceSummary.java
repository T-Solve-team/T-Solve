package vn.tsolve.platform.dto;

import java.util.Optional;
import java.util.UUID;

public record WorkspaceSummary(
        UUID id,
        String siteName,
        WorkspaceStatus status,
        String googleDomain,
        Optional<String> keycloakOrganizationId,
        Optional<String> jiraSiteUrl,
        Optional<String> jiraCloudId) {} // AC-001.34
