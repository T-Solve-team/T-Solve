package vn.tsolve.review.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
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

    @PostMapping("/send-back")
    @Operation(operationId = "sendBackTickets", summary = "Send expired tickets back to the Review Queue")
    @ApiResponse(responseCode = "400")
    @ApiResponse(responseCode = "403")
    @ApiResponse(responseCode = "404")
    @ApiResponse(responseCode = "409")
    @PreAuthorize("hasRole('" + Roles.PROJECT_MANAGER + "')")
    public DecisionResult sendBack(@AuthenticationPrincipal TSolveUser actor, @Valid @RequestBody ApproveRequest body) {
        var managed = managedProjects(actor);
        return new DecisionResult(decisions.sendBack(actor.id(), managed, body.ticketIds()));
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
        var managed = managedProjects(actor);
        return new DecisionResult(decisions.decide(actor.id(), managed, ticketIds, action, feedback));
    }

    private Set<UUID> managedProjects(TSolveUser actor) {
        if (actor == null) {
            throw new AccessDeniedException("Workspace Account required");
        }
        return workspaceAccess
                .flatMap(access -> access.managedProjectIds(actor.id()))
                .orElseThrow(() -> new AccessDeniedException("Verified Workspace access required"));
    }

    public record ApproveRequest(@NotEmpty @Size(max = 500) List<@NotNull UUID> ticketIds) {}

    public record RejectRequest(
            @NotEmpty @Size(max = 500) List<@NotNull UUID> ticketIds, @NotBlank @Size(max = 1000) String reason) {}

    public record RequestChangesRequest(
            @NotEmpty @Size(max = 500) List<@NotNull UUID> ticketIds, @NotBlank @Size(max = 1000) String comment) {}

    public record DecisionResult(int count) {}
}
