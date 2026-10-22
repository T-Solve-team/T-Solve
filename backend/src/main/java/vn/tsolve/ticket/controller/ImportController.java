package vn.tsolve.ticket.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.nio.charset.StandardCharsets;
import java.security.Principal;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.web.PagedModel;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import vn.tsolve.ticket.dto.ImportPreviewResponse;
import vn.tsolve.ticket.dto.ImportPreviewRowResponse;
import vn.tsolve.ticket.dto.ImportPreviewRowSummary;
import vn.tsolve.ticket.dto.ImportResultResponse;
import vn.tsolve.ticket.dto.RemoveRowsRequest;
import vn.tsolve.ticket.entity.PreviewRowStatus;
import vn.tsolve.ticket.service.CsvImportService;
import vn.tsolve.ticket.service.ImportConfirmService;
import vn.tsolve.ticket.service.ImportPreviewService;

/** Import tickets from CSV (UC-21); contract in frontend/openapi/tsolve-api.yaml, tag "import". */
@RestController
@RequestMapping("/api/v1/imports")
@Tag(name = "import")
@PreAuthorize(ImportController.PROJECT_MANAGER)
public class ImportController {

    static final String PROJECT_MANAGER = "hasRole('PROJECT_MANAGER')";

    private static final MediaType TEXT_CSV = new MediaType("text", "csv", StandardCharsets.UTF_8);

    private final CsvImportService csvImports;
    private final ImportPreviewService previews;
    private final ImportConfirmService confirms;

    public ImportController(CsvImportService csvImports, ImportPreviewService previews, ImportConfirmService confirms) {
        this.csvImports = csvImports;
        this.previews = previews;
        this.confirms = confirms;
    }

    @GetMapping("/csv/template")
    @Operation(operationId = "downloadCsvTemplate", summary = "The CSV template — header row only, UTF-8.")
    @ApiResponse(responseCode = "403")
    public ResponseEntity<String> downloadCsvTemplate() {
        return ResponseEntity.ok()
                .contentType(TEXT_CSV)
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment()
                                .filename("ticket-import-template.csv")
                                .build()
                                .toString())
                .body(csvImports.template());
    }

    @PostMapping(path = "/csv", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(operationId = "uploadCsvImport", summary = "Uploads one CSV file and returns its Import Preview.")
    @ApiResponse(responseCode = "400")
    @ApiResponse(responseCode = "403")
    @ApiResponse(responseCode = "413")
    public ResponseEntity<ImportPreviewResponse> uploadCsvImport(
            @RequestParam("file") MultipartFile file, @RequestParam long projectId, Principal user) {
        return ResponseEntity.ok(csvImports.upload(user.getName(), projectId, file));
    }

    @GetMapping("/previews/{previewId}/rows")
    @Operation(operationId = "getImportPreviewRows", summary = "One page of the preview rows.")
    @ApiResponse(responseCode = "403")
    @ApiResponse(responseCode = "404")
    public ResponseEntity<PagedModel<ImportPreviewRowSummary>> getImportPreviewRows(
            @PathVariable long previewId,
            @RequestParam(required = false) PreviewRowStatus status,
            @PageableDefault(size = 20) Pageable page,
            Principal user) {
        return ResponseEntity.ok(previews.rows(user.getName(), previewId, status, page));
    }

    @GetMapping("/previews/{previewId}/rows/{rowNo}")
    @Operation(operationId = "getImportPreviewRow", summary = "One preview row in full.")
    @ApiResponse(responseCode = "403")
    @ApiResponse(responseCode = "404")
    public ResponseEntity<ImportPreviewRowResponse> getImportPreviewRow(
            @PathVariable long previewId, @PathVariable int rowNo, Principal user) {
        return ResponseEntity.ok(previews.row(user.getName(), previewId, rowNo));
    }

    @PostMapping("/previews/{previewId}/remove-rows")
    @Operation(operationId = "removeImportPreviewRows", summary = "Marks rows Removed so Confirm skips them.")
    @ApiResponse(responseCode = "400")
    @ApiResponse(responseCode = "403")
    @ApiResponse(responseCode = "404")
    public ResponseEntity<ImportPreviewResponse> removeImportPreviewRows(
            @PathVariable long previewId, @Valid @RequestBody RemoveRowsRequest request, Principal user) {
        return ResponseEntity.ok(previews.removeRows(user.getName(), previewId, request.rowNumbers()));
    }

    @PostMapping("/previews/{previewId}/confirm")
    @Operation(operationId = "confirmImport", summary = "Imports the remaining Valid rows as Published tickets.")
    @ApiResponse(responseCode = "403")
    @ApiResponse(responseCode = "404")
    @ApiResponse(responseCode = "409")
    @ApiResponse(responseCode = "500")
    public ResponseEntity<ImportResultResponse> confirmImport(@PathVariable long previewId, Principal user) {
        return ResponseEntity.ok(confirms.confirm(user.getName(), previewId));
    }
}
