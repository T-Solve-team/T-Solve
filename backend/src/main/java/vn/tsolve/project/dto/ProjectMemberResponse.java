package vn.tsolve.project.dto;

import java.time.Instant;
import java.util.UUID;

public record ProjectMemberResponse(UUID userId, String fullName, String email, Role role, Instant dateAdded) {

    public enum Role {
        PROJECT_MANAGER,
        MEMBER
    }
}
