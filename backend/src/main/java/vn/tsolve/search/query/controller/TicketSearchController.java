package vn.tsolve.search.query.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.data.web.PagedModel;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vn.tsolve.common.security.Roles;
import vn.tsolve.common.security.TSolveUser;
import vn.tsolve.search.query.dto.TicketSearchQuery;
import vn.tsolve.search.query.dto.TicketSearchResultResponse;
import vn.tsolve.search.query.service.TicketSearchService;

@RestController
@RequestMapping("/api/v1/search/tickets")
@Tag(name = "search")
public class TicketSearchController {
    private final TicketSearchService tickets;

    public TicketSearchController(TicketSearchService tickets) {
        this.tickets = tickets;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('" + Roles.TICKET_RESOLVER + "','" + Roles.PROJECT_MANAGER + "')")
    @Operation(operationId = "searchTickets", summary = "Search Published tickets in the signed-in user's Departments")
    @ApiResponse(responseCode = "400", description = "MSG01, MSG02, MSG06 or MSG39")
    @ApiResponse(responseCode = "403", description = "MSG08")
    public ResponseEntity<PagedModel<TicketSearchResultResponse>> searchTickets(
            @RequestParam(required = false) @NotBlank @Size(max = 200) String q,
            @RequestParam(required = false) String departmentId,
            @RequestParam(required = false) String source,
            @RequestParam(required = false) String resolvedFrom,
            @RequestParam(required = false) String resolvedTo,
            @RequestParam(required = false) String page,
            @RequestParam(required = false) String size,
            @AuthenticationPrincipal TSolveUser user) {
        return ResponseEntity.ok(new PagedModel<>(tickets.search(
                user, TicketSearchQuery.parse(q, departmentId, source, resolvedFrom, resolvedTo, page, size))));
    }
}
