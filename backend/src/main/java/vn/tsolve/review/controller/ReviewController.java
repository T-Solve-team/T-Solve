package vn.tsolve.review.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.web.PagedModel;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vn.tsolve.review.dto.ApproveTicketsRequest;
import vn.tsolve.review.dto.RejectTicketsRequest;
import vn.tsolve.review.dto.RequestChangesRequest;
import vn.tsolve.review.dto.ReviewAction;
import vn.tsolve.review.dto.ReviewDecisionResult;
import vn.tsolve.review.dto.ReviewQueueItem;
import vn.tsolve.review.dto.ReviewTicketResponse;
import vn.tsolve.review.service.ReviewService;

@RestController
@Tag(name = "review")
@ApiResponse(responseCode = "400", description = "Invalid request")
@ApiResponse(responseCode = "403", description = "Project access denied")
@ApiResponse(responseCode = "404", description = "Ticket not found")
@ApiResponse(responseCode = "409", description = "Review state conflict")
public class ReviewController {
    private static final String MANAGER = "hasRole('PROJECT_MANAGER')";
    private static final String READER =
            "hasAnyRole('PROJECT_MANAGER','TICKET_RESOLVER','EMPLOYEE'," + "'ADMIN','KNOWLEDGE_MANAGER')";
    private final ReviewService service;

    public ReviewController(ReviewService service) {
        this.service = service;
    }

    @GetMapping("/api/v1/review-queue")
    @PreAuthorize(MANAGER)
    @Operation(operationId = "getReviewQueue", summary = "Pending tickets in managed Projects")
    public ResponseEntity<PagedModel<ReviewQueueItem>> queue(
            @RequestParam(required = false) Long projectId,
            @ParameterObject @PageableDefault(size = 20, sort = "reviewDueDate") Pageable pageable) {
        return ResponseEntity.ok(new PagedModel<>(service.queue(projectId, pageable)));
    }

    @GetMapping("/api/v1/review-queue/ticket-ids")
    @PreAuthorize(MANAGER)
    @Operation(operationId = "getReviewQueueTicketIds", summary = "All queued ticket IDs matching Project scope")
    public ResponseEntity<TicketIdList> ids(@RequestParam(required = false) Long projectId) {
        return ResponseEntity.ok(new TicketIdList(service.queueTicketIds(projectId)));
    }

    @GetMapping("/api/v1/tickets/{ticketId}")
    @PreAuthorize(READER)
    @Operation(operationId = "getReviewTicket", summary = "Read ticket review detail within authorized Projects")
    public ResponseEntity<ReviewTicketResponse> get(@PathVariable long ticketId) {
        return ResponseEntity.ok(service.get(ticketId));
    }

    @PostMapping("/api/v1/reviews/approve")
    @PreAuthorize(MANAGER)
    @Operation(operationId = "approveTickets", summary = "Approve and publish queued tickets")
    public ResponseEntity<ReviewDecisionResult> approve(@Valid @RequestBody ApproveTicketsRequest request) {
        return ResponseEntity.ok(service.decide(request.ticketIds(), ReviewAction.APPROVE, null));
    }

    @PostMapping("/api/v1/reviews/reject")
    @PreAuthorize(MANAGER)
    @Operation(operationId = "rejectTickets", summary = "Reject queued tickets with one reason")
    public ResponseEntity<ReviewDecisionResult> reject(@Valid @RequestBody RejectTicketsRequest request) {
        return ResponseEntity.ok(service.decide(request.ticketIds(), ReviewAction.REJECT, request.reason()));
    }

    @PostMapping("/api/v1/reviews/request-changes")
    @PreAuthorize(MANAGER)
    @Operation(operationId = "requestTicketChanges", summary = "Request changes to selected tickets with one comment")
    public ResponseEntity<ReviewDecisionResult> changes(@Valid @RequestBody RequestChangesRequest request) {
        return ResponseEntity.ok(service.decide(request.ticketIds(), ReviewAction.REQUEST_CHANGES, request.comment()));
    }

    public record TicketIdList(List<Long> ticketIds) {}
}
