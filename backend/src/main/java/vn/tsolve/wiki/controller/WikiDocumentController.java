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
import vn.tsolve.wiki.dto.WikiDocumentDetail;
import vn.tsolve.wiki.dto.WikiDocumentSummary;
import vn.tsolve.wiki.service.WikiBrowseService;
import vn.tsolve.wiki.service.WikiSearchService;

@RestController
@RequestMapping("/api/v1/wiki/documents")
@Tag(name = "wiki")
public class WikiDocumentController {
    private final WikiBrowseService browse;
    private final WikiSearchService search;

    public WikiDocumentController(WikiBrowseService browse, WikiSearchService search) {
        this.browse = browse;
        this.search = search;
    }

    @GetMapping("/search")
    @Operation(operationId = "searchWikiDocuments", summary = "Search viewable documents by words and meaning")
    @ApiResponse(responseCode = "400", description = "MSG01: q required; MSG02: q longer than 200 characters")
    @PreAuthorize("hasRole('" + Roles.EMPLOYEE + "')")
    public ResponseEntity<List<WikiDocumentSummary>> search(
            @AuthenticationPrincipal TSolveUser user, @RequestParam(required = false) String q) {
        return ResponseEntity.ok(search.search(user.id(), q));
    }

    @GetMapping("/{documentId}")
    @Operation(operationId = "getWikiDocument", summary = "Read a viewable Wiki document")
    @ApiResponse(responseCode = "404", description = "MSG110: document unknown or unavailable")
    @PreAuthorize("hasRole('" + Roles.EMPLOYEE + "')")
    public ResponseEntity<WikiDocumentDetail> getDocument(
            @AuthenticationPrincipal TSolveUser user, @PathVariable UUID documentId) {
        return ResponseEntity.ok(browse.getDocument(user.id(), documentId));
    }
}
