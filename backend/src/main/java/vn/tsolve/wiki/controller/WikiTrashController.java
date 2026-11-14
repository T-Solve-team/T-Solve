package vn.tsolve.wiki.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.tsolve.common.security.Roles;
import vn.tsolve.common.security.TSolveUser;
import vn.tsolve.wiki.dto.WikiRestoreResult;
import vn.tsolve.wiki.dto.WikiTrashItem;
import vn.tsolve.wiki.service.WikiTrashService;

@RestController
@RequestMapping("/api/v1/wiki")
@Tag(name = "wiki")
public class WikiTrashController {
    private final WikiTrashService service;

    public WikiTrashController(WikiTrashService service) {
        this.service = service;
    }

    @DeleteMapping("/documents/{documentId}")
    @Operation(operationId = "trashWikiDocument", summary = "Move a Published document to trash")
    @ApiResponse(responseCode = "403", description = "MSG08: viewable document outside the managed Space")
    @ApiResponse(responseCode = "404", description = "MSG110: document unknown, unavailable or not Published")
    @PreAuthorize("hasRole('" + Roles.EMPLOYEE + "')")
    public ResponseEntity<Void> trash(@AuthenticationPrincipal TSolveUser user, @PathVariable UUID documentId) {
        service.trash(user.id(), documentId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/spaces/{spaceId}/trash")
    @Operation(operationId = "listWikiTrash", summary = "List a managed Space's trash, newest first")
    @ApiResponse(responseCode = "403", description = "MSG08: not the Space's Knowledge Manager")
    @ApiResponse(responseCode = "404", description = "MSG04: Space unknown or inactive")
    @PreAuthorize("hasRole('" + Roles.KNOWLEDGE_MANAGER + "')")
    public ResponseEntity<List<WikiTrashItem>> listTrash(
            @AuthenticationPrincipal TSolveUser user, @PathVariable UUID spaceId) {
        return ResponseEntity.ok(service.listTrash(user.id(), spaceId));
    }

    @PostMapping("/documents/{documentId}/restore")
    @Operation(operationId = "restoreWikiDocument", summary = "Restore a trashed document to its folder")
    @ApiResponse(responseCode = "403", description = "MSG08: viewable document outside the managed Space")
    @ApiResponse(responseCode = "404", description = "MSG110: document unknown, unavailable or not in trash")
    @ApiResponse(responseCode = "409", description = "MSG84: chosen title taken by a concurrent request")
    @PreAuthorize("hasRole('" + Roles.EMPLOYEE + "')")
    public ResponseEntity<WikiRestoreResult> restore(
            @AuthenticationPrincipal TSolveUser user, @PathVariable UUID documentId) {
        return ResponseEntity.ok(service.restore(user.id(), documentId));
    }

    @DeleteMapping("/documents/{documentId}/permanent")
    @Operation(operationId = "deleteWikiDocumentPermanently", summary = "Permanently delete a trashed document")
    @ApiResponse(responseCode = "403", description = "MSG08: viewable document outside the managed Space")
    @ApiResponse(responseCode = "404", description = "MSG110: document unknown, unavailable or not in trash")
    @PreAuthorize("hasRole('" + Roles.EMPLOYEE + "')")
    public ResponseEntity<Void> deletePermanently(
            @AuthenticationPrincipal TSolveUser user, @PathVariable UUID documentId) {
        service.deletePermanently(user.id(), documentId);
        return ResponseEntity.noContent().build();
    }
}
