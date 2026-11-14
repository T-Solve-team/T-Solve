package vn.tsolve.ticket.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vn.tsolve.common.security.TSolveUser;
import vn.tsolve.ticket.dto.ManualEntryResponse;
import vn.tsolve.ticket.service.ManualEntryService;

/** Manual entry of one resolved ticket (UC-22); contract in frontend/openapi/tsolve-api.yaml, tag "import". */
@RestController
@RequestMapping("/api/v1/imports/manual")
@Tag(name = "import")
@PreAuthorize(ImportController.PROJECT_MANAGER)
public class ManualEntryController {

    private final ManualEntryService manualEntries;

    public ManualEntryController(ManualEntryService manualEntries) {
        this.manualEntries = manualEntries;
    }

    // "entry" is taken as a form field, not a @RequestPart: a browser's FormData gives a text part no content
    // type, and a typed part would then be rejected with 415 (design: entry part).
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(operationId = "saveManualEntry", summary = "Saves a ticket entered by hand as a Published Ticket.")
    @ApiResponse(responseCode = "400")
    @ApiResponse(responseCode = "403")
    @ApiResponse(responseCode = "409")
    @ApiResponse(responseCode = "500")
    public ResponseEntity<ManualEntryResponse> saveManualEntry(
            @AuthenticationPrincipal TSolveUser user, @RequestParam("entry") String entry) {
        return ResponseEntity.status(HttpStatus.CREATED).body(manualEntries.save(user, entry));
    }
}
