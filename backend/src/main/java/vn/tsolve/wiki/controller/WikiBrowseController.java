package vn.tsolve.wiki.controller;

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
import vn.tsolve.common.security.Roles;
import vn.tsolve.common.security.TSolveUser;
import vn.tsolve.wiki.dto.WikiDocumentSummary;
import vn.tsolve.wiki.dto.WikiFolder;
import vn.tsolve.wiki.dto.WikiSpace;
import vn.tsolve.wiki.service.WikiBrowseService;

@RestController
@RequestMapping("/api/v1/wiki")
@Tag(name = "wiki")
public class WikiBrowseController {
    private final WikiBrowseService service;

    public WikiBrowseController(WikiBrowseService service) {
        this.service = service;
    }

    @GetMapping("/spaces")
    @Operation(operationId = "listWikiSpaces", summary = "List viewable Spaces by name")
    @PreAuthorize("hasRole('" + Roles.EMPLOYEE + "')")
    public ResponseEntity<List<WikiSpace>> listSpaces(@AuthenticationPrincipal TSolveUser user) {
        return ResponseEntity.ok(service.listSpaces(user.id()));
    }

    @GetMapping("/company-wide")
    @Operation(operationId = "listCompanyWideDocuments", summary = "List Published Company-wide documents")
    @PreAuthorize("hasRole('" + Roles.EMPLOYEE + "')")
    public ResponseEntity<List<WikiDocumentSummary>> listCompanyWide(@AuthenticationPrincipal TSolveUser user) {
        return ResponseEntity.ok(service.listCompanyWide(user.id()));
    }

    @GetMapping("/spaces/{spaceId}/folders")
    @Operation(operationId = "listWikiFolders", summary = "List folders of a viewable Space")
    @ApiResponse(responseCode = "403", description = "MSG08: Space not viewable")
    @ApiResponse(responseCode = "404", description = "MSG04: Space not found or inactive")
    @PreAuthorize("hasRole('" + Roles.EMPLOYEE + "')")
    public ResponseEntity<List<WikiFolder>> listFolders(
            @AuthenticationPrincipal TSolveUser user, @PathVariable UUID spaceId) {
        return ResponseEntity.ok(service.listFolders(user.id(), spaceId));
    }

    @GetMapping("/spaces/{spaceId}/documents")
    @Operation(operationId = "listWikiDocuments", summary = "List Published documents directly in a folder")
    @ApiResponse(responseCode = "403", description = "MSG08: Space not viewable")
    @ApiResponse(responseCode = "404", description = "MSG04: Space or folder not found")
    @PreAuthorize("hasRole('" + Roles.EMPLOYEE + "')")
    public ResponseEntity<List<WikiDocumentSummary>> listDocuments(
            @AuthenticationPrincipal TSolveUser user,
            @PathVariable UUID spaceId,
            @RequestParam(required = false) UUID folderId) {
        return ResponseEntity.ok(service.listDocuments(user.id(), spaceId, folderId));
    }
}
