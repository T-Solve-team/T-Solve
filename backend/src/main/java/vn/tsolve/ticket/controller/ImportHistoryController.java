package vn.tsolve.ticket.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.data.web.PagedModel;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vn.tsolve.common.security.TSolveUser;
import vn.tsolve.ticket.dto.ImportHistoryItem;
import vn.tsolve.ticket.dto.ImportHistoryQuery;
import vn.tsolve.ticket.entity.TicketSource;
import vn.tsolve.ticket.service.ImportHistoryService;

@RestController
@RequestMapping("/api/v1/imports/history")
@Tag(name = "import")
public class ImportHistoryController {
    private final ImportHistoryService history;

    public ImportHistoryController(ImportHistoryService history) {
        this.history = history;
    }

    @GetMapping
    @PreAuthorize(ImportController.PROJECT_MANAGER)
    @Operation(
            operationId = "getImportHistory",
            summary = "Import Batches of the caller's managed Projects, newest first")
    @ApiResponse(responseCode = "400", description = "MSG39 or invalid query")
    @ApiResponse(responseCode = "403", description = "MSG08")
    public ResponseEntity<PagedModel<ImportHistoryItem>> getImportHistory(
            @RequestParam(required = false) UUID projectId,
            @RequestParam(required = false) TicketSource source,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @AuthenticationPrincipal TSolveUser user) {
        return ResponseEntity.ok(new PagedModel<>(
                history.list(user, new ImportHistoryQuery(projectId, source, dateFrom, dateTo, page, size))));
    }
}
