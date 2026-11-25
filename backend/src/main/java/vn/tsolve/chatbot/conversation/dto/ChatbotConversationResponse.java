package vn.tsolve.chatbot.conversation.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ChatbotConversationResponse(
        UUID chatSessionId, String title, Instant startedAt, List<ChatbotConversationTurnResponse> turns) {}
