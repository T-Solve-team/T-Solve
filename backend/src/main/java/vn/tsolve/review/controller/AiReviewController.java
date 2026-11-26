package vn.tsolve.review.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import java.util.Optional;
import java.util.UUID;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.tsolve.common.security.Roles;
import vn.tsolve.common.security.TSolveUser;
import vn.tsolve.review.dto.AiReviewResult;
import vn.tsolve.review.service.AiReviewService;
import vn.tsolve.review.service.ReviewWorkspaceAccess;

@RestController
@RequestMapping("/api/v1/tickets")
public class AiReviewController {

    private final AiReviewService reviews;
    private final Optional<ReviewWorkspaceAccess> workspaceAccess;

    public AiReviewController(AiReviewService reviews, Optional<ReviewWorkspaceAccess> workspaceAccess) {
        this.reviews = reviews;
        this.workspaceAccess = workspaceAccess;
    }

    @PostMapping("/{ticketId}/ai-review")
    @Operation(operationId = "runAiReview", summary = "Advisory AI check of a Pending review Solution")
    @ApiResponse(responseCode = "403")
    @ApiResponse(responseCode = "404")
    @ApiResponse(responseCode = "409")
    @ApiResponse(responseCode = "503")
    @PreAuthorize("hasRole('" + Roles.PROJECT_MANAGER + "')")
    public AiReviewResult review(@AuthenticationPrincipal TSolveUser actor, @PathVariable UUID ticketId) {
        // the same Workspace scope as the decisions (AC-009.3)
        if (actor == null) {
            throw new AccessDeniedException("Workspace Account required");
        }
        var managed = workspaceAccess
                .flatMap(access -> access.managedProjectIds(actor.id()))
                .orElseThrow(() -> new AccessDeniedException("Verified Workspace access required"));
        return reviews.review(managed, ticketId);
    }
}
