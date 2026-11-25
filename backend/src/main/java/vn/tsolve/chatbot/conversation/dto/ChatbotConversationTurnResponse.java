package vn.tsolve.chatbot.conversation.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import vn.tsolve.chatbot.answer.dto.ChatbotAnswerResponse;
import vn.tsolve.chatbot.rating.entity.ChatbotRating;

public record ChatbotConversationTurnResponse(
        String question,
        ChatbotAnswerResponse answer,
        @JsonInclude(JsonInclude.Include.ALWAYS) ChatbotRating rating) {}
