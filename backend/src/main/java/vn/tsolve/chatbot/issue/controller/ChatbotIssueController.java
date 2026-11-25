package vn.tsolve.chatbot.issue.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.tsolve.chatbot.issue.dto.ChatbotIssueDraftResponse;
import vn.tsolve.chatbot.issue.dto.ChatbotIssueRequest;
import vn.tsolve.chatbot.issue.dto.ChatbotJiraIssueResponse;
import vn.tsolve.chatbot.issue.service.ChatbotIssueService;
import vn.tsolve.common.security.TSolveUser;

@RestController
@RequestMapping("/api/v1/chatbot/answers/{answerId}")
@Tag(name = "chatbot")
public class ChatbotIssueController {
    private final ChatbotIssueService issues;

    public ChatbotIssueController(ChatbotIssueService issues) {
        this.issues = issues;
    }

    @PostMapping("/issue-draft")
    @PreAuthorize("isAuthenticated()")
    @Operation(operationId = "draftChatbotIssue", summary = "Draft a Jira issue from an answer")
    @ApiResponse(responseCode = "400", description = "MSG06")
    @ApiResponse(responseCode = "404", description = "MSG04")
    @ApiResponse(responseCode = "409", description = "MSG146")
    public ResponseEntity<ChatbotIssueDraftResponse> draftChatbotIssue(
            @AuthenticationPrincipal TSolveUser user, @PathVariable String answerId) {
        return ResponseEntity.ok(issues.draft(user, answerId));
    }

    @PostMapping("/jira-issue")
    @PreAuthorize("isAuthenticated()")
    @Operation(operationId = "createChatbotIssue", summary = "Create the Jira issue of an answer")
    @ApiResponse(responseCode = "400", description = "MSG01, MSG02 or MSG06")
    @ApiResponse(responseCode = "404", description = "MSG04")
    @ApiResponse(responseCode = "409", description = "MSG146")
    @ApiResponse(responseCode = "502", description = "MSG148 or MSG157")
    @ApiResponse(responseCode = "503", description = "MSG149")
    public ResponseEntity<ChatbotJiraIssueResponse> createChatbotIssue(
            @AuthenticationPrincipal TSolveUser user,
            @PathVariable String answerId,
            @Valid @RequestBody ChatbotIssueRequest request) {
        var result = issues.create(user, answerId, request);
        return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK)
                .body(result.issue());
    }
}
