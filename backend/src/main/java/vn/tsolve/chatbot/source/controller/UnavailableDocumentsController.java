package vn.tsolve.chatbot.source.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vn.tsolve.chatbot.source.dto.UnavailableDocumentsRequest;
import vn.tsolve.chatbot.source.dto.UnavailableDocumentsResponse;
import vn.tsolve.chatbot.source.service.WikiSources;
import vn.tsolve.common.security.TSolveUser;

@RestController
@RequestMapping("/api/v1/chatbot/unavailable-documents")
@Tag(name = "chatbot")
public class UnavailableDocumentsController {
    private final WikiSources sources;

    public UnavailableDocumentsController(WikiSources sources) {
        this.sources = sources;
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    @Operation(operationId = "listUnavailableCitedDocuments", summary = "List unavailable cited Wiki documents")
    @ApiResponse(responseCode = "400", description = "MSG06")
    @ApiResponse(responseCode = "401", description = "MSG07")
    @ApiResponse(responseCode = "403", description = "MSG08")
    public ResponseEntity<UnavailableDocumentsResponse> listUnavailableCitedDocuments(
            @AuthenticationPrincipal TSolveUser user, @RequestParam(required = false) List<String> wikiDocumentIds) {
        var request = UnavailableDocumentsRequest.of(wikiDocumentIds);
        var unavailableIds = sources.unavailable(user, request.wikiDocumentIds());
        return ResponseEntity.ok(UnavailableDocumentsResponse.of(request.wikiDocumentIds(), unavailableIds));
    }
}
