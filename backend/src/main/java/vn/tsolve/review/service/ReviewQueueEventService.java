package vn.tsolve.review.service;

import java.util.Set;
import java.util.UUID;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

public interface ReviewQueueEventService {

    /**
     * Open an event stream for a Project Manager of the Workspace. It signals {@code queue-changed} (Project id only)
     * for the Projects in {@code managedProjectIds} and sends a heartbeat comment while it is open.
     */
    SseEmitter subscribe(UUID workspaceId, Set<UUID> managedProjectIds);
}
