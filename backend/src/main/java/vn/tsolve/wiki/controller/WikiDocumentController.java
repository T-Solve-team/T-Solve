package vn.tsolve.wiki.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;
import vn.tsolve.common.security.Roles;
import vn.tsolve.common.security.TSolveUser;
import vn.tsolve.wiki.dto.WikiDocumentDetail;
import vn.tsolve.wiki.dto.WikiDocumentSummary;
import vn.tsolve.wiki.service.WikiBrowseService;
import vn.tsolve.wiki.service.WikiSearchService;
import vn.tsolve.wiki.service.WikiUploadService;

@RestController
@RequestMapping("/api/v1/wiki/documents")
@Tag(name = "wiki")
public class WikiDocumentController {
    private final WikiBrowseService browse;
    private final WikiSearchService search;
    private final WikiUploadService uploads;

    public WikiDocumentController(WikiBrowseService browse, WikiSearchService search, WikiUploadService uploads) {
        this.browse = browse;
        this.search = search;
        this.uploads = uploads;
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

    @GetMapping("/{documentId}/file")
    @Operation(operationId = "downloadWikiDocumentFile", summary = "Download the original file of a viewable document")
    @ApiResponse(responseCode = "404", description = "MSG110: document unknown or unavailable")
    @PreAuthorize("hasRole('" + Roles.EMPLOYEE + "')")
    public ResponseEntity<StreamingResponseBody> download(
            @AuthenticationPrincipal TSolveUser user, @PathVariable UUID documentId) {
        var file = uploads.download(user.id(), documentId);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(file.contentType()))
                .contentLength(file.fileSize())
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment()
                                .filename(file.fileName().replace("\r", "").replace("\n", ""), StandardCharsets.UTF_8)
                                .build()
                                .toString())
                .header("X-Content-Type-Options", "nosniff")
                .body(file.content());
    }
}
