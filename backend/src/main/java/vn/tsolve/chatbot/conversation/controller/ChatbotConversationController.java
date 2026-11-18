package vn.tsolve.chatbot.conversation.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.tsolve.chatbot.conversation.dto.ChatbotConversationResponse;
import vn.tsolve.chatbot.conversation.dto.ChatbotConversationSummaryResponse;
import vn.tsolve.chatbot.conversation.service.ChatbotConversationService;
import vn.tsolve.common.security.TSolveUser;

@RestController
@RequestMapping("/api/v1/chatbot/conversations")
@Tag(name = "chatbot")
public class ChatbotConversationController {
    private final ChatbotConversationService conversations;

    public ChatbotConversationController(ChatbotConversationService conversations) {
        this.conversations = conversations;
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    @Operation(operationId = "listChatbotConversations", summary = "List your saved conversations")
    @ApiResponse(responseCode = "401", description = "MSG07")
    public ResponseEntity<List<ChatbotConversationSummaryResponse>> listChatbotConversations(
            @AuthenticationPrincipal TSolveUser user) {
        return ResponseEntity.ok(conversations.list(user));
    }

    @GetMapping("/{chatSessionId}")
    @PreAuthorize("isAuthenticated()")
    @Operation(operationId = "getChatbotConversation", summary = "Open your saved conversation")
    @ApiResponse(responseCode = "401", description = "MSG07")
    @ApiResponse(responseCode = "404", description = "MSG04")
    public ResponseEntity<ChatbotConversationResponse> getChatbotConversation(
            @AuthenticationPrincipal TSolveUser user, @PathVariable String chatSessionId) {
        return ResponseEntity.ok(conversations.get(user, chatSessionId));
    }

    @DeleteMapping("/{chatSessionId}")
    @PreAuthorize("isAuthenticated()")
    @Operation(operationId = "deleteChatbotConversation", summary = "Delete your saved conversation")
    @ApiResponse(responseCode = "401", description = "MSG07")
    @ApiResponse(responseCode = "403", description = "MSG07")
    @ApiResponse(responseCode = "404", description = "MSG04")
    public ResponseEntity<Void> deleteChatbotConversation(
            @AuthenticationPrincipal TSolveUser user, @PathVariable String chatSessionId) {
        conversations.delete(user, chatSessionId);
        return ResponseEntity.noContent().build();
    }
}
