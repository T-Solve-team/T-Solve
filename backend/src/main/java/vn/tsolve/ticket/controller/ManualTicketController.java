package vn.tsolve.ticket.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import vn.tsolve.common.security.TSolveUser;
import vn.tsolve.ticket.dto.DraftSavedResponse;
import vn.tsolve.ticket.dto.EvidenceUpload;
import vn.tsolve.ticket.dto.ImportResultResponse;
import vn.tsolve.ticket.dto.ManualTicketDraft;
import vn.tsolve.ticket.dto.ManualTicketRequest;
import vn.tsolve.ticket.service.ManualTicketService;

/** Manual entry of a ticket with its Solution (UC-22); contract in frontend/openapi/tsolve-api.yaml, tag "import". */
@RestController
@RequestMapping("/api/v1/imports/manual")
@Tag(name = "import")
@PreAuthorize(ImportController.PROJECT_MANAGER)
public class ManualTicketController {

    private final ManualTicketService manualTickets;
    private final ObjectMapper objectMapper;

    public ManualTicketController(ManualTicketService manualTickets, ObjectMapper objectMapper) {
        this.manualTickets = manualTickets;
        this.objectMapper = objectMapper;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(operationId = "createManualTicket", summary = "Saves one manually entered ticket as Published (MSG32).")
    @ApiResponse(responseCode = "400")
    @ApiResponse(responseCode = "403")
    @ApiResponse(responseCode = "404")
    @ApiResponse(responseCode = "409")
    @ApiResponse(responseCode = "503")
    public ResponseEntity<ImportResultResponse> createManualTicket(
            @RequestPart("entry") String entry,
            @RequestPart(value = "files", required = false) List<MultipartFile> files,
            @AuthenticationPrincipal TSolveUser user,
            HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(manualTickets.create(user, readEntry(entry, request), uploads(files)));
    }

    @PostMapping(value = "/drafts", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(operationId = "saveManualTicketDraft", summary = "Saves the entry as a Manual Draft (MSG41).")
    @ApiResponse(responseCode = "400")
    @ApiResponse(responseCode = "403")
    @ApiResponse(responseCode = "404")
    @ApiResponse(responseCode = "503")
    public ResponseEntity<DraftSavedResponse> saveManualTicketDraft(
            @RequestPart("entry") String entry,
            @RequestPart(value = "files", required = false) List<MultipartFile> files,
            @AuthenticationPrincipal TSolveUser user,
            HttpServletRequest request) {
        return ResponseEntity.ok(manualTickets.saveDraft(user, readEntry(entry, request), uploads(files)));
    }

    @GetMapping("/drafts/{previewId}")
    @Operation(
            operationId = "getManualTicketDraft",
            summary = "One of the caller's Manual Drafts, every field as saved.")
    @ApiResponse(responseCode = "403")
    @ApiResponse(responseCode = "404")
    public ResponseEntity<ManualTicketDraft> getManualTicketDraft(
            @PathVariable long previewId, @AuthenticationPrincipal TSolveUser user) {
        return ResponseEntity.ok(manualTickets.getDraft(user, previewId));
    }

    // The generated client sends entry as a plain string part without a Content-Type.
    private ManualTicketRequest readEntry(String entry, HttpServletRequest request) {
        try {
            ManualTicketRequest parsed = objectMapper.readValue(entry, ManualTicketRequest.class);
            if (parsed == null) {
                throw new HttpMessageNotReadableException(
                        "Required entry is missing", new ServletServerHttpRequest(request));
            }
            return parsed;
        } catch (JsonProcessingException e) {
            throw new HttpMessageNotReadableException(
                    "Could not read manual entry", e, new ServletServerHttpRequest(request));
        }
    }

    private static List<EvidenceUpload> uploads(List<MultipartFile> files) {
        return files == null
                ? List.of()
                : files.stream()
                        .map(file -> new EvidenceUpload(
                                file.getOriginalFilename(), file.getContentType(), file.getSize(), file))
                        .toList();
    }
}
