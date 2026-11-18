package vn.tsolve.chatbot.conversation.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.web.PagedModel;
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
    private static final int DEFAULT_SIZE = 20;
    private static final int MAX_SIZE = 100;

    private final ChatbotConversationService conversations;

    public ChatbotConversationController(ChatbotConversationService conversations) {
        this.conversations = conversations;
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    @Operation(operationId = "listChatbotConversations", summary = "List your saved conversations")
    @ApiResponse(responseCode = "401", description = "MSG07")
    public ResponseEntity<PagedModel<ChatbotConversationSummaryResponse>> listChatbotConversations(
            @AuthenticationPrincipal TSolveUser user, @PageableDefault(size = DEFAULT_SIZE) Pageable page) {
        // AC-013.5: a size above 100 is read as 100 and a client sort is dropped (the order is fixed in the query).
        var fixed = PageRequest.of(page.getPageNumber(), Math.min(page.getPageSize(), MAX_SIZE));
        return ResponseEntity.ok(new PagedModel<>(conversations.list(user, fixed)));
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
