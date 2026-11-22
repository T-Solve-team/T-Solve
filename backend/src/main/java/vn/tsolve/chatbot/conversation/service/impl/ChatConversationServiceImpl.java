package vn.tsolve.chatbot.conversation.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.tsolve.chatbot.answer.dto.ChatbotCitation;
import vn.tsolve.chatbot.answer.dto.ChatbotTurnRequest;
import vn.tsolve.chatbot.conversation.entity.ChatMessage;
import vn.tsolve.chatbot.conversation.entity.ChatSession;
import vn.tsolve.chatbot.conversation.exception.ChatbotConversationNotFoundException;
import vn.tsolve.chatbot.conversation.repository.ChatMessageRepository;
import vn.tsolve.chatbot.conversation.repository.ChatSessionRepository;
import vn.tsolve.chatbot.conversation.service.ChatConversationService;

@Service
public class ChatConversationServiceImpl implements ChatConversationService {
    private static final TypeReference<List<ChatbotCitation>> CITATIONS = new TypeReference<>() {};
    private final ChatSessionRepository sessions;
    private final ChatMessageRepository messages;
    private final ObjectMapper json;

    public ChatConversationServiceImpl(
            ChatSessionRepository sessions, ChatMessageRepository messages, ObjectMapper json) {
        this.sessions = sessions;
        this.messages = messages;
        this.json = json;
    }

    @Override
    @Transactional(readOnly = true)
    public List<ChatbotTurnRequest> history(UUID userId, String chatSessionId) {
        UUID id;
        try {
            id = UUID.fromString(chatSessionId);
        } catch (IllegalArgumentException | NullPointerException invalid) {
            throw new ChatbotConversationNotFoundException();
        }
        var session = sessions.findByIdAndUserId(id, userId).orElseThrow(ChatbotConversationNotFoundException::new);
        return oldestFirst(messages.findTop10ByChatSessionIdOrderByCreatedAtDesc(session.getId()));
    }

    @Override
    @Transactional
    public UUID record(
            UUID userId,
            UUID chatSessionId,
            String title,
            UUID chatbotAnswerId,
            String question,
            String outcome,
            String answerText,
            List<ChatbotCitation> citations) {
        UUID sessionId;
        if (chatSessionId == null) {
            sessionId = sessions.saveAndFlush(new ChatSession(userId, title == null ? question : title))
                    .getId();
        } else {
            sessionId = chatSessionId;
            // BUG-374: the conversation was deleted after the ownership check; the transaction rolls back the answer.
            if (sessions.touch(sessionId) == 0) {
                throw new ChatbotConversationNotFoundException();
            }
        }
        messages.saveAndFlush(
                new ChatMessage(sessionId, chatbotAnswerId, question, outcome, answerText, write(citations)));
        return sessionId;
    }

    @Override
    @Transactional(readOnly = true)
    public List<ChatbotTurnRequest> turnsBefore(UUID chatbotAnswerId) {
        return messages.findByChatbotAnswerId(chatbotAnswerId)
                .map(message -> oldestFirst(messages.findTop10ByChatSessionIdAndCreatedAtLessThanOrderByCreatedAtDesc(
                        message.getChatSessionId(), message.getCreatedAt())))
                .orElse(List.of());
    }

    private static List<ChatbotTurnRequest> oldestFirst(List<ChatMessage> newestFirst) {
        List<ChatbotTurnRequest> turns = new ArrayList<>();
        for (ChatMessage message : newestFirst) {
            // answer_text is '' for every outcome but ANSWERED (AC-013.2).
            turns.add(new ChatbotTurnRequest(message.getQuestion(), message.getAnswerText()));
        }
        Collections.reverse(turns);
        return turns;
    }

    private String write(List<ChatbotCitation> citations) {
        try {
            return json.writerFor(CITATIONS).writeValueAsString(citations);
        } catch (JsonProcessingException impossible) {
            // The citations are plain records; no message, so no content reaches a log.
            throw new IllegalStateException("Citations not serializable");
        }
    }
}
