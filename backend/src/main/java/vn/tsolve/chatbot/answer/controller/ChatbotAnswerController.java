package vn.tsolve.chatbot.answer.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.tsolve.chatbot.answer.dto.ChatbotAnswerResponse;
import vn.tsolve.chatbot.answer.dto.ChatbotQuestionRequest;
import vn.tsolve.chatbot.answer.service.ChatbotAnswerService;
import vn.tsolve.common.security.TSolveUser;

@RestController
@RequestMapping("/api/v1/chatbot/answers")
@Tag(name = "chatbot")
public class ChatbotAnswerController {
    private final ChatbotAnswerService answers;

    public ChatbotAnswerController(ChatbotAnswerService answers) {
        this.answers = answers;
    }

    @PostMapping
    @PreAuthorize("isAuthenticated()")
    @Operation(operationId = "askChatbot", summary = "Answer a question from Workspace knowledge")
    @ApiResponse(responseCode = "400", description = "MSG01 or MSG02")
    @ApiResponse(responseCode = "401", description = "MSG07")
    @ApiResponse(responseCode = "403", description = "MSG08")
    @ApiResponse(responseCode = "503", description = "MSG06")
    public ResponseEntity<ChatbotAnswerResponse> askChatbot(
            @AuthenticationPrincipal TSolveUser user, @Valid @RequestBody ChatbotQuestionRequest request) {
        return ResponseEntity.ok(answers.ask(user, request));
    }
}
