package vn.tsolve.ticket.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.security.Principal;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.tsolve.ticket.dto.DraftSavedResponse;
import vn.tsolve.ticket.dto.ImportDraftSummary;
import vn.tsolve.ticket.service.ImportDraftService;

/** Drafts of a Jira import (GBR-26); contract in frontend/openapi/tsolve-api.yaml, tag "import". */
@RestController
@RequestMapping("/api/v1/imports")
@Tag(name = "import")
@PreAuthorize(ImportController.PROJECT_MANAGER)
public class ImportDraftController {

    private final ImportDraftService drafts;

    public ImportDraftController(ImportDraftService drafts) {
        this.drafts = drafts;
    }

    @PostMapping("/previews/{previewId}/draft")
    @Operation(operationId = "saveImportDraft", summary = "Saves the preview as a Draft (MSG41).")
    @ApiResponse(responseCode = "403")
    @ApiResponse(responseCode = "404")
    public ResponseEntity<DraftSavedResponse> saveImportDraft(@PathVariable long previewId, Principal user) {
        return ResponseEntity.ok(drafts.save(user.getName(), previewId));
    }

    @GetMapping("/drafts")
    @Operation(operationId = "listImportDrafts", summary = "The caller's own import Drafts, newest first.")
    @ApiResponse(responseCode = "403")
    public ResponseEntity<List<ImportDraftSummary>> listImportDrafts(Principal user) {
        return ResponseEntity.ok(drafts.list(user.getName()));
    }

    @DeleteMapping("/drafts/{previewId}")
    @Operation(operationId = "deleteImportDraft", summary = "Deletes one of the caller's Drafts with its rows.")
    @ApiResponse(responseCode = "403")
    @ApiResponse(responseCode = "404")
    public ResponseEntity<Void> deleteImportDraft(@PathVariable long previewId, Principal user) {
        drafts.delete(user.getName(), previewId);
        return ResponseEntity.noContent().build();
    }
}
