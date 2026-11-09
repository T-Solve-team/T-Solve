package vn.tsolve.search.suggestion.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.tsolve.common.security.Roles;
import vn.tsolve.common.security.TSolveUser;
import vn.tsolve.search.query.dto.TicketSearchResultResponse;
import vn.tsolve.search.suggestion.dto.TicketSuggestionRequest;
import vn.tsolve.search.suggestion.service.TicketSuggestionService;

@RestController
@RequestMapping("/api/v1/search/suggestions")
@Tag(name = "search")
public class TicketSuggestionController {
    private final TicketSuggestionService tickets;

    public TicketSuggestionController(TicketSuggestionService tickets) {
        this.tickets = tickets;
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('" + Roles.TICKET_RESOLVER + "','" + Roles.PROJECT_MANAGER + "')")
    @Operation(operationId = "suggestTickets", summary = "Suggest similar Published tickets for a Jira issue")
    @ApiResponse(responseCode = "400", description = "MSG01, MSG02 or MSG06")
    @ApiResponse(responseCode = "403", description = "MSG08")
    public ResponseEntity<List<TicketSearchResultResponse>> suggestTickets(
            @Valid @RequestBody TicketSuggestionRequest request, @AuthenticationPrincipal TSolveUser user) {
        return ResponseEntity.ok(tickets.suggest(user, request));
    }
}
