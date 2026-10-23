package vn.tsolve.review.dto;

import java.util.Map;
import java.util.Set;

/** Supplied only by the trusted auth/Project adapter, never deserialized from a review request. */
public record ReviewActor(String id, String name, Map<Long, String> managedProjects, Set<Long> readableProjects) {
    public ReviewActor {
        if (id == null || id.isBlank() || name == null || name.isBlank()) {
            throw new IllegalArgumentException("Verified actor identity is required");
        }
        managedProjects = Map.copyOf(managedProjects);
        readableProjects = Set.copyOf(readableProjects);
    }
}
