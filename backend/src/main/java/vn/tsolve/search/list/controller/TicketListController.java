package vn.tsolve.search.list.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
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
import vn.tsolve.search.list.dto.TicketListQuery;
import vn.tsolve.search.list.dto.TicketSummaryResponse;
import vn.tsolve.search.list.service.TicketListService;

@RestController
@RequestMapping("/api/v1/tickets")
@Tag(name = "tickets")
public class TicketListController {
    private final TicketListService tickets;

    public TicketListController(TicketListService tickets) {
        this.tickets = tickets;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('" + Roles.TICKET_RESOLVER + "','" + Roles.PROJECT_MANAGER + "')")
    @Operation(operationId = "getTickets", summary = "One page of the tickets the signed-in user may see")
    @ApiResponse(responseCode = "400", description = "MSG02 or MSG06")
    @ApiResponse(responseCode = "403", description = "MSG08")
    public ResponseEntity<PagedModel<TicketSummaryResponse>> getTickets(
            @RequestParam(required = false) @Size(max = 100) String keyword,
            @RequestParam(required = false) String departmentId,
            @RequestParam(required = false) String source,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String sort,
            @AuthenticationPrincipal TSolveUser user) {
        return ResponseEntity.ok(new PagedModel<>(
                tickets.list(user, new TicketListQuery(keyword, departmentId, source, status, page, size, sort))));
    }
}
