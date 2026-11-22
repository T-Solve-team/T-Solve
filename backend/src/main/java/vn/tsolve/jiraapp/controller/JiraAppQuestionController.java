package vn.tsolve.jiraapp.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.tsolve.common.security.JiraAppPrincipal;
import vn.tsolve.common.security.Roles;
import vn.tsolve.jiraapp.dto.JiraAppQuestionJob;
import vn.tsolve.jiraapp.dto.JiraAppQuestionJobResult;
import vn.tsolve.jiraapp.dto.JiraAppQuestionRequest;
import vn.tsolve.jiraapp.dto.JiraAppRatingRequest;
import vn.tsolve.jiraapp.dto.JiraAskerEligibility;
import vn.tsolve.jiraapp.service.JiraAppQuestionService;

@RestController
@RequestMapping("/api/v1/jira-app/questions")
@Tag(name = "jira-app")
public class JiraAppQuestionController {

    private final JiraAppQuestionService questions;

    public JiraAppQuestionController(JiraAppQuestionService questions) {
        this.questions = questions;
    }

    @GetMapping("/eligibility")
    @PreAuthorize("hasRole('" + Roles.JIRA_APP + "')")
    @Operation(operationId = "getJiraAskerEligibility", summary = "Whether the Jira user may ask T-Solve")
    public ResponseEntity<JiraAskerEligibility> eligibility(@AuthenticationPrincipal JiraAppPrincipal caller) {
        return ResponseEntity.ok(questions.eligibility(caller));
    }

    @PostMapping
    @PreAuthorize("hasRole('" + Roles.JIRA_APP + "')")
    @Operation(operationId = "askJiraAppQuestion", summary = "Start a question job from the Ask T-Solve page")
    @ApiResponse(responseCode = "400", description = "MSG01 or MSG02")
    @ApiResponse(responseCode = "403", description = "MSG124 or MSG08")
    @ApiResponse(responseCode = "503", description = "MSG06")
    public ResponseEntity<JiraAppQuestionJob> askJiraAppQuestion(
            @AuthenticationPrincipal JiraAppPrincipal caller, @Valid @RequestBody JiraAppQuestionRequest request) {
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(questions.start(caller, request));
    }

    @GetMapping("/{jobId}")
    @PreAuthorize("hasRole('" + Roles.JIRA_APP + "')")
    @Operation(operationId = "getJiraAppQuestionJob", summary = "Read an Ask T-Solve question job")
    @ApiResponse(responseCode = "404", description = "MSG06")
    @ApiResponse(responseCode = "503", description = "MSG06")
    public ResponseEntity<JiraAppQuestionJobResult> getJiraAppQuestionJob(
            @AuthenticationPrincipal JiraAppPrincipal caller, @PathVariable UUID jobId) {
        return ResponseEntity.ok(questions.result(caller, jobId));
    }

    @PutMapping("/{answerId}/rating")
    @PreAuthorize("hasRole('" + Roles.JIRA_APP + "')")
    @Operation(operationId = "rateJiraAppAnswer", summary = "Rate the last answer of the Ask T-Solve page")
    @ApiResponse(responseCode = "400", description = "MSG01")
    @ApiResponse(responseCode = "403", description = "MSG124 or MSG08")
    @ApiResponse(responseCode = "404", description = "MSG04")
    public ResponseEntity<Void> rateJiraAppAnswer(
            @AuthenticationPrincipal JiraAppPrincipal caller,
            @PathVariable UUID answerId,
            @Valid @RequestBody JiraAppRatingRequest request) {
        questions.rate(caller, answerId, request);
        return ResponseEntity.noContent().build();
    }
}
