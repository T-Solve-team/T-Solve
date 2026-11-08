package vn.tsolve.review.controller;

import io.swagger.v3.oas.annotations.Operation;
import java.util.Optional;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import vn.tsolve.common.security.Roles;
import vn.tsolve.common.security.TSolveUser;
import vn.tsolve.identity.signin.port.TenantScope;
import vn.tsolve.review.service.ReviewQueueEventService;
import vn.tsolve.review.service.ReviewWorkspaceAccess;

@RestController
@RequestMapping("/api/v1")
public class ReviewQueueEventController {

    private final ReviewQueueEventService events;
    private final TenantScope tenant;
    private final Optional<ReviewWorkspaceAccess> workspaceAccess;

    public ReviewQueueEventController(
            ReviewQueueEventService events, TenantScope tenant, Optional<ReviewWorkspaceAccess> workspaceAccess) {
        this.events = events;
        this.tenant = tenant;
        this.workspaceAccess = workspaceAccess;
    }

    @GetMapping(value = "/review-queue/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(operationId = "streamReviewQueueEvents")
    @PreAuthorize("hasRole('" + Roles.PROJECT_MANAGER + "')")
    public SseEmitter stream(@AuthenticationPrincipal TSolveUser actor) {
        if (actor == null) {
            throw new AccessDeniedException("Workspace Account required");
        }
        var managed = workspaceAccess
                .flatMap(access -> access.managedProjectIds(actor.id()))
                .filter(projects -> !projects.isEmpty())
                .orElseThrow(() -> new AccessDeniedException("A managed Project in a verified Workspace is required"));
        return events.subscribe(tenant.currentWorkspaceId(), actor.id(), managed);
    }
}
