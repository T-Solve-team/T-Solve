package vn.tsolve.wiki.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.tsolve.common.security.Roles;
import vn.tsolve.common.security.TSolveUser;
import vn.tsolve.wiki.dto.CreateWikiFolderRequest;
import vn.tsolve.wiki.dto.UpdateWikiFolderRequest;
import vn.tsolve.wiki.dto.WikiFolder;
import vn.tsolve.wiki.service.WikiFolderService;

@RestController
@RequestMapping("/api/v1/wiki")
@Tag(name = "wiki")
public class WikiFolderController {
    private final WikiFolderService service;

    public WikiFolderController(WikiFolderService service) {
        this.service = service;
    }

    @PostMapping("/spaces/{spaceId}/folders")
    @Operation(operationId = "createWikiFolder", summary = "Create a folder in a managed Space")
    @ApiResponse(responseCode = "400", description = "MSG01/MSG02: name; MSG86: parent depth")
    @ApiResponse(responseCode = "403", description = "MSG08: not the Space's Knowledge Manager")
    @ApiResponse(responseCode = "404", description = "MSG04: Space or parent not found")
    @ApiResponse(responseCode = "409", description = "MSG85: sibling name already used")
    @PreAuthorize("hasRole('" + Roles.KNOWLEDGE_MANAGER + "')")
    public ResponseEntity<WikiFolder> create(
            @AuthenticationPrincipal TSolveUser user,
            @PathVariable UUID spaceId,
            @Valid @RequestBody CreateWikiFolderRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(user.id(), spaceId, request));
    }

    @PutMapping("/folders/{folderId}")
    @Operation(operationId = "updateWikiFolder", summary = "Rename or move a folder and its sub-tree")
    @ApiResponse(responseCode = "400", description = "MSG01/MSG02: name; MSG86: depth or cycle")
    @ApiResponse(responseCode = "403", description = "MSG08: not the Space's Knowledge Manager")
    @ApiResponse(responseCode = "404", description = "MSG04: folder, parent or Space not found")
    @ApiResponse(responseCode = "409", description = "MSG85: sibling name already used")
    @PreAuthorize("hasRole('" + Roles.KNOWLEDGE_MANAGER + "')")
    public ResponseEntity<WikiFolder> update(
            @AuthenticationPrincipal TSolveUser user,
            @PathVariable UUID folderId,
            @Valid @RequestBody UpdateWikiFolderRequest request) {
        return ResponseEntity.ok(service.update(user.id(), folderId, request));
    }

    @DeleteMapping("/folders/{folderId}")
    @Operation(operationId = "deleteWikiFolder", summary = "Delete an empty folder")
    @ApiResponse(responseCode = "403", description = "MSG08: not the Space's Knowledge Manager")
    @ApiResponse(responseCode = "404", description = "MSG04: folder or Space not found")
    @ApiResponse(responseCode = "409", description = "MSG87: direct sub-folder and document counts")
    @PreAuthorize("hasRole('" + Roles.KNOWLEDGE_MANAGER + "')")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal TSolveUser user, @PathVariable UUID folderId) {
        service.delete(user.id(), folderId);
        return ResponseEntity.noContent().build();
    }
}
