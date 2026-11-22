package vn.tsolve.wiki.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import vn.tsolve.common.security.Roles;
import vn.tsolve.common.security.TSolveUser;
import vn.tsolve.wiki.dto.WikiDocumentRef;
import vn.tsolve.wiki.dto.WikiUploadPreview;
import vn.tsolve.wiki.service.WikiUploadService;

@RestController
@RequestMapping("/api/v1/wiki/spaces/{spaceId}")
@Tag(name = "wiki")
public class WikiUploadController {
    private final WikiUploadService uploads;

    public WikiUploadController(WikiUploadService uploads) {
        this.uploads = uploads;
    }

    @PostMapping(value = "/uploads/extract", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(operationId = "extractWikiUploads", summary = "Preview PDF or DOCX files without storing them")
    @ApiResponse(responseCode = "400", description = "MSG01: files required; MSG88: too many; MSG89: invalid file type")
    @ApiResponse(responseCode = "403", description = "MSG08: not the Space's Knowledge Manager")
    @ApiResponse(responseCode = "404", description = "MSG04: unknown or inactive Space")
    @ApiResponse(responseCode = "413", description = "MSG28: file larger than 10 MB")
    @PreAuthorize("hasRole('" + Roles.KNOWLEDGE_MANAGER + "')")
    public ResponseEntity<List<WikiUploadPreview>> extract(
            @AuthenticationPrincipal TSolveUser user,
            @PathVariable UUID spaceId,
            @RequestPart(required = false) List<MultipartFile> files) {
        return ResponseEntity.ok(uploads.extract(user.id(), spaceId, files));
    }

    @PostMapping(value = "/documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(operationId = "publishWikiDocuments", summary = "Publish PDF or DOCX documents in a managed Space")
    @ApiResponse(
            responseCode = "400",
            description = "MSG01, MSG02: metadata; MSG06: invalid document type; MSG88, MSG89, MSG90: files")
    @ApiResponse(responseCode = "403", description = "MSG08: not the Space's Knowledge Manager")
    @ApiResponse(responseCode = "404", description = "MSG04: unknown or inactive Space or folder")
    @ApiResponse(responseCode = "409", description = "MSG84: title already used")
    @ApiResponse(responseCode = "413", description = "MSG28: file larger than 10 MB")
    @PreAuthorize("hasRole('" + Roles.KNOWLEDGE_MANAGER + "')")
    public ResponseEntity<List<WikiDocumentRef>> publish(
            @AuthenticationPrincipal TSolveUser user,
            @PathVariable UUID spaceId,
            @RequestPart(required = false) List<MultipartFile> files,
            @RequestPart(required = false) String metadata) {
        return ResponseEntity.status(201).body(uploads.publish(user.id(), spaceId, files, metadata));
    }
}
