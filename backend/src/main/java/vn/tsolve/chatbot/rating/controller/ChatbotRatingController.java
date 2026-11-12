package vn.tsolve.chatbot.rating.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.tsolve.chatbot.rating.dto.ChatbotRatingRequest;
import vn.tsolve.chatbot.rating.service.ChatbotRatingService;
import vn.tsolve.common.security.TSolveUser;

@RestController
@RequestMapping("/api/v1/chatbot/answers/{answerId}/rating")
@Tag(name = "chatbot")
public class ChatbotRatingController {
    private final ChatbotRatingService ratings;

    public ChatbotRatingController(ChatbotRatingService ratings) {
        this.ratings = ratings;
    }

    @PutMapping
    @PreAuthorize("isAuthenticated()")
    @Operation(operationId = "rateChatbotAnswer", summary = "Rate your chatbot answer")
    @ApiResponse(responseCode = "400", description = "MSG06")
    @ApiResponse(responseCode = "401", description = "MSG07")
    @ApiResponse(responseCode = "404", description = "MSG04")
    public ResponseEntity<Void> rateChatbotAnswer(
            @AuthenticationPrincipal TSolveUser user,
            @PathVariable String answerId,
            @RequestBody(required = false) ChatbotRatingRequest request) {
        ratings.rate(user, answerId, request);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping
    @PreAuthorize("isAuthenticated()")
    @Operation(operationId = "removeChatbotAnswerRating", summary = "Remove your chatbot answer rating")
    @ApiResponse(responseCode = "401", description = "MSG07")
    @ApiResponse(responseCode = "404", description = "MSG04")
    public ResponseEntity<Void> removeChatbotAnswerRating(
            @AuthenticationPrincipal TSolveUser user, @PathVariable String answerId) {
        ratings.remove(user, answerId);
        return ResponseEntity.noContent().build();
    }
}
