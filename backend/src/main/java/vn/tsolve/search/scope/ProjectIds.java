package vn.tsolve.search.scope;

import java.util.UUID;

public final class ProjectIds {
    private ProjectIds() {}

    // ponytail: depends on import-ticket-list D10's BIGINT-to-ticket-UUID mapping; drop when project.project_id becomes
    // UUID.
    public static UUID ticketProjectId(long id) {
        return new UUID(0L, id);
    }
}
