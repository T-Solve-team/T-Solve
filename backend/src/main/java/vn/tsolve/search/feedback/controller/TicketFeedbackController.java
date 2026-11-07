package vn.tsolve.search.feedback.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import vn.tsolve.common.security.Roles;
import vn.tsolve.common.security.TSolveUser;
import vn.tsolve.search.feedback.dto.TicketFeedbackRequest;
import vn.tsolve.search.feedback.service.TicketFeedbackService;

@RestController
@RequestMapping("/api/v1/tickets")
@Tag(name = "feedback")
public class TicketFeedbackController {
    private final TicketFeedbackService feedback;

    public TicketFeedbackController(TicketFeedbackService feedback) {
        this.feedback = feedback;
    }

    @PutMapping("/{ticketId}/feedback")
    @PreAuthorize("hasAnyRole('" + Roles.TICKET_RESOLVER + "','" + Roles.PROJECT_MANAGER + "')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(operationId = "giveFeedback", summary = "Saves the user's Feedback on a Published ticket")
    @ApiResponse(responseCode = "400", description = "MSG01, MSG02 or MSG06")
    @ApiResponse(responseCode = "403", description = "MSG08")
    @ApiResponse(responseCode = "404", description = "MSG04")
    public ResponseEntity<Void> giveFeedback(
            @PathVariable UUID ticketId,
            @Valid @RequestBody TicketFeedbackRequest request,
            @AuthenticationPrincipal TSolveUser user) {
        feedback.give(user, ticketId, request);
        return ResponseEntity.noContent().build();
    }
}
