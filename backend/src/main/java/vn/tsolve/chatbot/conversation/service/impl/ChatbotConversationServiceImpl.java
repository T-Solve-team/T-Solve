package vn.tsolve.chatbot.conversation.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import vn.tsolve.chatbot.answer.dto.ChatbotAnswerResponse;
import vn.tsolve.chatbot.answer.dto.ChatbotCitation;
import vn.tsolve.chatbot.answer.entity.ChatbotAnswer;
import vn.tsolve.chatbot.answer.repository.ChatbotAnswerRepository;
import vn.tsolve.chatbot.conversation.dto.ChatbotConversationResponse;
import vn.tsolve.chatbot.conversation.dto.ChatbotConversationSummaryResponse;
import vn.tsolve.chatbot.conversation.dto.ChatbotConversationTurnResponse;
import vn.tsolve.chatbot.conversation.entity.ChatSession;
import vn.tsolve.chatbot.conversation.exception.ChatbotConversationNotFoundException;
import vn.tsolve.chatbot.conversation.repository.ChatMessageRepository;
import vn.tsolve.chatbot.conversation.repository.ChatSessionRepository;
import vn.tsolve.chatbot.conversation.service.ChatbotConversationService;
import vn.tsolve.common.audit.AuditAction;
import vn.tsolve.common.audit.AuditService;
import vn.tsolve.common.security.TSolveUser;

@Service
public class ChatbotConversationServiceImpl implements ChatbotConversationService {
    private static final TypeReference<List<ChatbotCitation>> CITATIONS = new TypeReference<>() {};
    private final ChatSessionRepository sessions;
    private final ChatMessageRepository messages;
    private final ChatbotAnswerRepository answers;
    private final ObjectMapper json;
    private final AuditService audit;

    public ChatbotConversationServiceImpl(
            ChatSessionRepository sessions,
            ChatMessageRepository messages,
            ChatbotAnswerRepository answers,
            ObjectMapper json,
            AuditService audit) {
        this.sessions = sessions;
        this.messages = messages;
        this.answers = answers;
        this.json = json;
        this.audit = audit;
    }

    @Override
    @Transactional(readOnly = true)
    public List<ChatbotConversationSummaryResponse> list(TSolveUser user) {
        return sessions.findByUserIdOrderByStartedAtDesc(user.id()).stream()
                .map(session -> new ChatbotConversationSummaryResponse(
                        session.getId(), session.getTitle(), session.getStartedAt()))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public ChatbotConversationResponse get(TSolveUser user, String chatSessionId) {
        var session = sessions.findByIdAndUserId(parseId(chatSessionId), user.id())
                .orElseThrow(ChatbotConversationNotFoundException::new);
        var stored = messages.findByChatSessionIdOrderByCreatedAtAsc(session.getId());
        var ids = stored.stream().map(message -> message.getChatbotAnswerId()).toList();
        var current =
                answers.findAllById(ids).stream().collect(Collectors.toMap(ChatbotAnswer::getId, Function.identity()));
        var turns = stored.stream()
                .map(message -> new ChatbotConversationTurnResponse(
                        message.getQuestion(),
                        new ChatbotAnswerResponse(
                                message.getChatbotAnswerId(),
                                ChatbotAnswerResponse.Outcome.valueOf(message.getOutcome()),
                                message.getAnswerText(),
                                readCitations(message.getCitations())),
                        current.get(message.getChatbotAnswerId()).getRating()))
                .toList();
        return new ChatbotConversationResponse(session.getId(), session.getTitle(), session.getStartedAt(), turns);
    }

    @Override
    @Transactional
    public void delete(TSolveUser user, String chatSessionId) {
        var session = sessions.findForUpdateByIdAndUserId(parseId(chatSessionId), user.id())
                .orElseThrow(ChatbotConversationNotFoundException::new);
        deleteSession(session);
        audit.record(AuditAction.DELETE, "chat_session", session.getId());
    }

    @Override
    @Transactional(readOnly = true)
    public List<UUID> findExpiredIds(Instant cutoff) {
        return sessions.findIdsLastMessageAtBefore(cutoff);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean deleteExpired(UUID chatSessionId, Instant cutoff) {
        var session = sessions.findExpiredForUpdate(chatSessionId, cutoff);
        if (session.isEmpty()) {
            return false;
        }
        deleteSession(session.get());
        return true;
    }

    private void deleteSession(ChatSession session) {
        var ids = messages.findAnswerIdsByChatSessionId(session.getId());
        sessions.delete(session);
        // The cascade must remove message foreign keys before the answer delete runs.
        sessions.flush();
        if (!ids.isEmpty()) {
            answers.deleteDisposableByIds(ids);
        }
    }

    private static UUID parseId(String value) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException | NullPointerException invalid) {
            throw new ChatbotConversationNotFoundException();
        }
    }

    private List<ChatbotCitation> readCitations(String value) {
        try {
            return json.readValue(value, CITATIONS);
        } catch (JsonProcessingException invalid) {
            // Jackson's error includes input content; do not attach it to a logged exception.
            throw new IllegalStateException("Stored citations could not be read");
        }
    }
}
