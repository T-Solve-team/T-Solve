package vn.tsolve.ticket.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.security.Principal;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.tsolve.ticket.dto.ImportPreviewResponse;
import vn.tsolve.ticket.dto.JiraPullRequest;
import vn.tsolve.ticket.service.JiraImportService;

/** Import resolved issues from Jira (UC-20); contract in frontend/openapi/tsolve-api.yaml, tag "import". */
@RestController
@RequestMapping("/api/v1/imports")
@Tag(name = "import")
@PreAuthorize(ImportController.PROJECT_MANAGER)
public class JiraImportController {

    private final JiraImportService jiraImports;

    public JiraImportController(JiraImportService jiraImports) {
        this.jiraImports = jiraImports;
    }

    @PostMapping("/jira")
    @Operation(operationId = "pullJiraImport", summary = "Pulls resolved Jira issues into a new Import Preview.")
    @ApiResponse(responseCode = "400")
    @ApiResponse(responseCode = "403")
    @ApiResponse(responseCode = "404")
    @ApiResponse(responseCode = "502")
    public ResponseEntity<ImportPreviewResponse> pullJiraImport(
            @Valid @RequestBody JiraPullRequest request, Principal user) {
        return ResponseEntity.ok(
                jiraImports.pull(user.getName(), request.projectId(), request.resolvedFrom(), request.resolvedTo()));
    }
}
