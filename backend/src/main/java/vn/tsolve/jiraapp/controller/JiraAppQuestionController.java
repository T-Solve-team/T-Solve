package vn.tsolve.jiraapp.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.tsolve.chatbot.answer.dto.ChatbotAnswerResponse;
import vn.tsolve.common.security.JiraAppPrincipal;
import vn.tsolve.common.security.Roles;
import vn.tsolve.jiraapp.dto.JiraAppQuestionRequest;
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
    @Operation(operationId = "askJiraAppQuestion", summary = "Answer a question from the Ask T-Solve page")
    @ApiResponse(responseCode = "400", description = "MSG01 or MSG02")
    @ApiResponse(responseCode = "403", description = "MSG62")
    @ApiResponse(responseCode = "503", description = "MSG06")
    public ResponseEntity<ChatbotAnswerResponse> askJiraAppQuestion(
            @AuthenticationPrincipal JiraAppPrincipal caller, @Valid @RequestBody JiraAppQuestionRequest request) {
        return ResponseEntity.ok(questions.ask(caller, request));
    }
}
