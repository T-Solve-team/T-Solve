package vn.tsolve.project.dto;

import java.util.UUID;

/** {@code projectManager} is null unless the Project Manager membership names an Active account (AC-004.5). */
public record ProjectRowResponse(
        UUID projectId, String jiraProjectKey, ProjectManagerRef projectManager, int memberCount, String status) {

    public record ProjectManagerRef(UUID userId, String fullName) {}
}
