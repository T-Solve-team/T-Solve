package vn.tsolve.ticket.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.tsolve.common.security.TSolveUser;
import vn.tsolve.ticket.dto.DraftSavedResponse;
import vn.tsolve.ticket.dto.ImportResultResponse;
import vn.tsolve.ticket.dto.ManualTicketDraft;
import vn.tsolve.ticket.dto.ManualTicketRequest;
import vn.tsolve.ticket.service.ManualTicketService;

/** Manual entry of a ticket with its Solution (UC-22); contract in frontend/openapi/tsolve-api.yaml, tag "import". */
@RestController
@RequestMapping("/api/v1/imports/manual")
@Tag(name = "import")
@PreAuthorize(ImportController.PROJECT_MANAGER)
public class ManualTicketController {

    private final ManualTicketService manualTickets;

    public ManualTicketController(ManualTicketService manualTickets) {
        this.manualTickets = manualTickets;
    }

    @PostMapping
    @Operation(operationId = "createManualTicket", summary = "Saves one manually entered ticket as Published (MSG32).")
    @ApiResponse(responseCode = "400")
    @ApiResponse(responseCode = "403")
    @ApiResponse(responseCode = "404")
    @ApiResponse(responseCode = "409")
    public ResponseEntity<ImportResultResponse> createManualTicket(
            @RequestBody ManualTicketRequest request, @AuthenticationPrincipal TSolveUser user) {
        return ResponseEntity.status(HttpStatus.CREATED).body(manualTickets.create(user, request));
    }

    @PostMapping("/drafts")
    @Operation(operationId = "saveManualTicketDraft", summary = "Saves the entry as a Manual Draft (MSG41).")
    @ApiResponse(responseCode = "400")
    @ApiResponse(responseCode = "403")
    @ApiResponse(responseCode = "404")
    public ResponseEntity<DraftSavedResponse> saveManualTicketDraft(
            @RequestBody ManualTicketRequest request, @AuthenticationPrincipal TSolveUser user) {
        return ResponseEntity.ok(manualTickets.saveDraft(user, request));
    }

    @GetMapping("/drafts/{previewId}")
    @Operation(
            operationId = "getManualTicketDraft",
            summary = "One of the caller's Manual Drafts, every field as saved.")
    @ApiResponse(responseCode = "403")
    @ApiResponse(responseCode = "404")
    public ResponseEntity<ManualTicketDraft> getManualTicketDraft(
            @PathVariable long previewId, @AuthenticationPrincipal TSolveUser user) {
        return ResponseEntity.ok(manualTickets.getDraft(user, previewId));
    }
}
