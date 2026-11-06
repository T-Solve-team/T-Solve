package vn.tsolve.review.controller;

import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import vn.tsolve.common.security.Roles;
import vn.tsolve.common.security.TSolveUser;
import vn.tsolve.review.service.ReviewWorkspaceAccess;
import vn.tsolve.ticket.service.TicketDecisionService;
import vn.tsolve.ticket.service.TicketDecisionService.Action;

@RestController
@RequestMapping("/api/v1/reviews")
@PreAuthorize("hasRole('" + Roles.PROJECT_MANAGER + "')")
public class ReviewDecisionController {

    private final TicketDecisionService decisions;
    private final Optional<ReviewWorkspaceAccess> workspaceAccess;

    public ReviewDecisionController(TicketDecisionService decisions, Optional<ReviewWorkspaceAccess> workspaceAccess) {
        this.decisions = decisions;
        this.workspaceAccess = workspaceAccess;
    }

    @PostMapping("/approve")
    @Operation(operationId = "approveTickets")
    public DecisionResult approve(@AuthenticationPrincipal TSolveUser actor, @Valid @RequestBody ApproveRequest body) {
        return decide(actor, body.ticketIds(), Action.APPROVED, null);
    }

    @PostMapping("/reject")
    @Operation(operationId = "rejectTickets")
    public DecisionResult reject(@AuthenticationPrincipal TSolveUser actor, @Valid @RequestBody RejectRequest body) {
        return decide(actor, body.ticketIds(), Action.REJECTED, body.reason());
    }

    @PostMapping("/request-changes")
    @Operation(operationId = "requestTicketChanges")
    public DecisionResult requestChanges(
            @AuthenticationPrincipal TSolveUser actor, @Valid @RequestBody RequestChangesRequest body) {
        return decide(actor, body.ticketIds(), Action.CHANGES_REQUESTED, body.comment());
    }

    private DecisionResult decide(TSolveUser actor, List<UUID> ticketIds, Action action, String feedback) {
        if (actor == null) {
            throw new AccessDeniedException("Workspace Account required");
        }
        var managedProjects = workspaceAccess
                .flatMap(access -> access.managedProjectIds(actor.id()))
                .orElseThrow(() -> new AccessDeniedException("Verified Workspace access required"));
        if (new HashSet<>(ticketIds).size() != ticketIds.size() || (feedback != null && feedback.isBlank())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        }
        return new DecisionResult(decisions.decide(actor.id(), managedProjects, ticketIds, action, feedback));
    }

    public record ApproveRequest(@NotEmpty @Size(max = 500) List<@NotNull UUID> ticketIds) {}

    public record RejectRequest(
            @NotEmpty @Size(max = 500) List<@NotNull UUID> ticketIds, @NotBlank @Size(max = 1000) String reason) {}

    public record RequestChangesRequest(
            @NotEmpty @Size(max = 500) List<@NotNull UUID> ticketIds, @NotBlank @Size(max = 1000) String comment) {}

    public record DecisionResult(int count) {}
}
