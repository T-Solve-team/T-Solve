package vn.tsolve.chatbot.conversation.service;

import java.util.List;
import java.util.UUID;
import vn.tsolve.chatbot.answer.dto.ChatbotCitation;
import vn.tsolve.chatbot.answer.dto.ChatbotTurnRequest;

/** The conversations of web users (spec STORY-013). */
public interface ChatConversationService {
    /**
     * The last 10 turns of the user's own conversation, oldest first (AC-013.2); throws
     * {@code ChatbotConversationNotFoundException} (404 MSG04) for an id that is not a UUID or not theirs (AC-013.4).
     */
    List<ChatbotTurnRequest> history(UUID userId, String chatSessionId);

    /**
     * Stores one answered turn and returns its conversation id: a new conversation titled with {@code title}, or the
     * question when it is null, when {@code chatSessionId} is null (AC-013.1), else that one, whose last message time moves to now (AC-013.3). Joins
     * the caller's transaction, so the turn is stored with its {@code chatbot_answer} row or not at all.
     */
    UUID record(
            UUID userId,
            UUID chatSessionId,
            String title,
            UUID chatbotAnswerId,
            String question,
            String outcome,
            String answerText,
            List<ChatbotCitation> citations);

    /** The last 10 turns before this answer in its conversation, oldest first; none when it has no conversation. */
    List<ChatbotTurnRequest> turnsBefore(UUID chatbotAnswerId);
}
