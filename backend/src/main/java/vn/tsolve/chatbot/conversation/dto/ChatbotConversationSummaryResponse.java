package vn.tsolve.chatbot.conversation.dto;

import java.time.Instant;
import java.util.UUID;

public record ChatbotConversationSummaryResponse(UUID chatSessionId, String title, Instant startedAt) {}
