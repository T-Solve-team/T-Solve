package vn.tsolve.jiraapp.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vn.tsolve.common.security.JiraAppPrincipal;
import vn.tsolve.common.security.Roles;
import vn.tsolve.jiraapp.dto.JiraAppTicketPreview;
import vn.tsolve.jiraapp.dto.JiraAppTicketResult;
import vn.tsolve.jiraapp.service.JiraAppTicketService;

@RestController
@RequestMapping("/api/v1/jira-app/tickets")
@Tag(name = "jira-app")
public class JiraAppTicketController {

    private final JiraAppTicketService tickets;

    public JiraAppTicketController(JiraAppTicketService tickets) {
        this.tickets = tickets;
    }

    @GetMapping("/search")
    @PreAuthorize("hasRole('" + Roles.JIRA_APP + "')")
    @Operation(operationId = "searchJiraAppTickets", summary = "Published tickets of the Departments of the viewer")
    @ApiResponse(responseCode = "400", description = "MSG01 or MSG02 for the query")
    @ApiResponse(responseCode = "403", description = "MSG62 or MSG08")
    @ApiResponse(
            responseCode = "503",
            description = "MSG06: the search is unavailable or the token could not be checked")
    public ResponseEntity<List<JiraAppTicketResult>> searchJiraAppTickets(
            @AuthenticationPrincipal JiraAppPrincipal caller, @RequestParam(required = false) String q) {
        return ResponseEntity.ok(tickets.search(caller, q));
    }

    @GetMapping("/{ticketUuid}")
    @PreAuthorize("hasRole('" + Roles.JIRA_APP + "')")
    @Operation(operationId = "getJiraAppTicket", summary = "A Published ticket for the Published Ticket Preview")
    @ApiResponse(responseCode = "400", description = "Invalid ticket id")
    @ApiResponse(responseCode = "403", description = "MSG62 or MSG08")
    @ApiResponse(responseCode = "503", description = "MSG06: the Forge token could not be checked")
    public ResponseEntity<JiraAppTicketPreview> getJiraAppTicket(
            @AuthenticationPrincipal JiraAppPrincipal caller, @PathVariable UUID ticketUuid) {
        return ResponseEntity.ok(tickets.preview(caller, ticketUuid));
    }
}
