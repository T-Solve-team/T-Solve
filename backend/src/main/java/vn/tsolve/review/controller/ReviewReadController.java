package vn.tsolve.review.controller;

import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.web.PagedModel;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vn.tsolve.common.security.Role;
import vn.tsolve.common.security.Roles;
import vn.tsolve.common.security.TSolveUser;
import vn.tsolve.review.service.ReviewWorkspaceAccess;
import vn.tsolve.ticket.dto.TicketReviewView.Detail;
import vn.tsolve.ticket.dto.TicketReviewView.HistoryFilter;
import vn.tsolve.ticket.dto.TicketReviewView.HistoryItem;
import vn.tsolve.ticket.dto.TicketReviewView.ProjectAccess;
import vn.tsolve.ticket.dto.TicketReviewView.QueueItem;
import vn.tsolve.ticket.service.TicketReviewReadService;

@RestController
@RequestMapping("/api/v1")
public class ReviewReadController {

    private final TicketReviewReadService reads;
    private final Optional<ReviewWorkspaceAccess> workspaceAccess;

    public ReviewReadController(TicketReviewReadService reads, Optional<ReviewWorkspaceAccess> workspaceAccess) {
        this.reads = reads;
        this.workspaceAccess = workspaceAccess;
    }

    @GetMapping("/review-queue")
    @Operation(operationId = "getReviewQueue")
    @PreAuthorize("hasRole('" + Roles.PROJECT_MANAGER + "')")
    public PagedModel<QueueItem> queue(
            @AuthenticationPrincipal TSolveUser actor,
            @RequestParam(required = false) UUID departmentId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam MultiValueMap<String, String> parameters) {
        return new PagedModel<>(reads.queue(
                projects(actor), departmentId, PageRequest.of(page, size), parameters.getOrDefault("sort", List.of())));
    }

    @GetMapping("/review-queue/ticket-ids")
    @Operation(operationId = "getReviewQueueTicketIds")
    @PreAuthorize("hasRole('" + Roles.PROJECT_MANAGER + "')")
    public TicketIdList eligibleIds(
            @AuthenticationPrincipal TSolveUser actor, @RequestParam(required = false) UUID departmentId) {
        return new TicketIdList(reads.eligibleIds(projects(actor), departmentId));
    }

    @GetMapping("/review-history")
    @Operation(operationId = "getReviewHistory")
    @PreAuthorize("hasRole('" + Roles.PROJECT_MANAGER + "')")
    public PagedModel<HistoryItem> history(
            @AuthenticationPrincipal TSolveUser actor,
            @RequestParam(required = false) UUID departmentId,
            @RequestParam(required = false) String decision,
            @RequestParam(required = false) String dateFrom,
            @RequestParam(required = false) String dateTo,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam MultiValueMap<String, String> parameters) {
        var filter = new HistoryFilter(
                departmentId, decision, dateFrom, dateTo, page, size, parameters.getOrDefault("sort", List.of()));
        return new PagedModel<>(reads.history(projects(actor), filter));
    }

    @GetMapping("/tickets/{ticketId}")
    @Operation(operationId = "getReviewTicketDetail")
    @PreAuthorize("hasRole('" + Roles.EMPLOYEE + "')")
    public Detail detail(@AuthenticationPrincipal TSolveUser actor, @PathVariable UUID ticketId) {
        var projects = projects(actor);
        return reads.detail(projects, actor.id(), actor.roles().contains(Role.PROJECT_MANAGER), ticketId);
    }

    private Map<UUID, ProjectAccess> projects(TSolveUser actor) {
        if (actor == null) {
            throw new AccessDeniedException("Workspace Account required");
        }
        return workspaceAccess
                .flatMap(access -> access.readableProjects(actor.id()))
                .orElseThrow(() -> new AccessDeniedException("Verified Workspace access required"));
    }

    public record TicketIdList(List<UUID> ticketIds) {}
}
