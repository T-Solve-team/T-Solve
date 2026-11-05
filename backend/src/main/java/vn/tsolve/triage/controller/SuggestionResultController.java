package vn.tsolve.triage.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.web.PagedModel;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vn.tsolve.triage.dto.HistoryQuery;
import vn.tsolve.triage.dto.SuggestionResultDetail;
import vn.tsolve.triage.dto.SuggestionResultSummary;
import vn.tsolve.triage.service.SuggestionResultService;

@RestController
@RequestMapping("/api/v1/suggestion-results")
@Tag(name = "triage")
public class SuggestionResultController {

    static final String PROJECT_MANAGER = "hasRole('PROJECT_MANAGER')";
    static final String SIGNED_IN = "isAuthenticated()";
    private static final String PROJECT_MANAGER_AUTHORITY = "ROLE_PROJECT_MANAGER";

    private final SuggestionResultService results;

    public SuggestionResultController(SuggestionResultService results) {
        this.results = results;
    }

    @GetMapping
    @PreAuthorize(PROJECT_MANAGER)
    @Operation(operationId = "getSuggestionResults", summary = "Suggestion results of the Projects I manage")
    @ApiResponse(responseCode = "400", description = "MSG06 or MSG39")
    @ApiResponse(responseCode = "403", description = "MSG08")
    public ResponseEntity<PagedModel<SuggestionResultSummary>> getSuggestionResults(
            @RequestParam(required = false) String projectId,
            @RequestParam(required = false) String answer,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String sort,
            Authentication user) {
        HistoryQuery query = new HistoryQuery(projectId, answer, from, to, page, size, sort);
        return ResponseEntity.ok(new PagedModel<>(results.history(user.getName(), query)));
    }

    // Any signed-in caller reaches the service: a non-Project Manager gets the same 404 as a missing result (AC-004.2).
    @GetMapping("/{issueKey}")
    @PreAuthorize(SIGNED_IN)
    @Operation(operationId = "getSuggestionResult", summary = "One result of a Project I manage")
    @ApiResponse(responseCode = "404", description = "MSG04")
    public ResponseEntity<SuggestionResultDetail> getSuggestionResult(
            @PathVariable String issueKey, Authentication user) {
        boolean projectManager = user.getAuthorities().stream()
                .anyMatch(authority -> PROJECT_MANAGER_AUTHORITY.equals(authority.getAuthority()));
        return ResponseEntity.ok(results.detail(user.getName(), projectManager, issueKey));
    }
}
