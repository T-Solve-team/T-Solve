package vn.tsolve.chatbot.conversation.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import vn.tsolve.chatbot.answer.dto.ChatbotAnswerResponse.Outcome;
import vn.tsolve.chatbot.answer.dto.WikiDocumentCitation;
import vn.tsolve.chatbot.answer.entity.ChatbotAnswer;
import vn.tsolve.chatbot.answer.repository.ChatbotAnswerRepository;
import vn.tsolve.chatbot.conversation.entity.ChatMessage;
import vn.tsolve.chatbot.conversation.entity.ChatSession;
import vn.tsolve.chatbot.conversation.exception.ChatbotConversationNotFoundException;
import vn.tsolve.chatbot.conversation.repository.ChatMessageRepository;
import vn.tsolve.chatbot.conversation.repository.ChatSessionRepository;
import vn.tsolve.chatbot.rating.entity.ChatbotRating;
import vn.tsolve.common.audit.AuditAction;
import vn.tsolve.common.audit.AuditService;
import vn.tsolve.common.security.TSolveUser;

class ChatbotConversationServiceImplTest {
    private final ChatSessionRepository sessions = mock(ChatSessionRepository.class);
    private final ChatMessageRepository messages = mock(ChatMessageRepository.class);
    private final ChatbotAnswerRepository answers = mock(ChatbotAnswerRepository.class);
    private final AuditService audit = mock(AuditService.class);
    private final ChatbotConversationServiceImpl service =
            new ChatbotConversationServiceImpl(sessions, messages, answers, new ObjectMapper(), audit);
    private final TSolveUser user = new TSolveUser(UUID.randomUUID(), "owner@example.test", "Owner", Set.of());
    private final UUID id = UUID.randomUUID();

    @Test
    void listsOwnConversationsNewestFirst_AC_013_5() {
        var newer = session(id, "Newer", Instant.parse("2026-11-18T00:00:00Z"));
        var older = session(UUID.randomUUID(), "Older", Instant.parse("2026-11-17T00:00:00Z"));
        var request = PageRequest.of(1, 2);
        when(sessions.findByUserIdOrderByStartedAtDescIdDesc(user.id(), request))
                .thenReturn(new PageImpl<>(List.of(newer, older), request, 5));

        var page = service.list(user, request);
        assertThat(page.getContent()).extracting("chatSessionId").containsExactly(id, older.getId());
        assertThat(page.getContent().getFirst().title()).isEqualTo("Newer");
        assertThat(page.getContent().getFirst().startedAt()).isEqualTo(newer.getStartedAt());
        assertThat(page.getTotalElements()).isEqualTo(5);
        verifyNoInteractions(messages, answers, audit);
    }

    @Test
    void opensEveryStoredTurnWithCitationsAndCurrentRating_AC_013_6() {
        var session = session(id, "Title", Instant.parse("2026-11-17T00:00:00Z"));
        when(sessions.findByIdAndUserId(id, user.id())).thenReturn(Optional.of(session));
        UUID document = UUID.randomUUID();
        var answered = message(
                UUID.randomUUID(),
                "Question",
                "ANSWERED",
                "Stored [1]",
                "[{\"type\":\"WIKI_DOCUMENT\",\"number\":1,\"wikiDocumentId\":\"" + document
                        + "\",\"title\":\"Citation\"}]");
        var greeting = message(UUID.randomUUID(), "Hi", "GREETING", "", "[]");
        UUID answeredId = answered.getChatbotAnswerId();
        UUID greetingId = greeting.getChatbotAnswerId();
        when(messages.findByChatSessionIdOrderByCreatedAtAsc(id)).thenReturn(List.of(answered, greeting));
        var rated = mock(ChatbotAnswer.class);
        when(rated.getId()).thenReturn(answeredId);
        when(rated.getRating()).thenReturn(ChatbotRating.NOT_HELPFUL);
        var unrated = mock(ChatbotAnswer.class);
        when(unrated.getId()).thenReturn(greetingId);
        when(answers.findAllById(List.of(answered.getChatbotAnswerId(), greeting.getChatbotAnswerId())))
                .thenReturn(List.of(unrated, rated));

        var result = service.get(user, id.toString());
        assertThat(result).isNotNull();
        assertThat(result.chatSessionId()).isEqualTo(id);
        assertThat(result.title()).isEqualTo("Title");
        assertThat(result.startedAt()).isEqualTo(session.getStartedAt());
        assertThat(result.turns()).extracting("question").containsExactly("Question", "Hi");
        var first = result.turns().getFirst();
        assertThat(first.answer().answerId()).isEqualTo(answered.getChatbotAnswerId());
        assertThat(first.answer().outcome()).isEqualTo(Outcome.ANSWERED);
        assertThat(first.answer().text()).isEqualTo("Stored [1]");
        assertThat(first.answer().citations()).containsExactly(new WikiDocumentCitation(1, document, "Citation"));
        assertThat(first.answer().chatSessionId()).isNull();
        assertThat(first.rating()).isEqualTo(ChatbotRating.NOT_HELPFUL);
        assertThat(result.turns().getLast().rating()).isNull();
        assertThat(result.turns().getLast().answer().outcome()).isEqualTo(Outcome.GREETING);
        assertThat(result.turns().getLast().answer().citations()).isEmpty();
    }

    @Test
    void deletesMessagesBeforeDisposableAnswersAndAudits_AC_013_7() {
        var session = session(id, "Private title", Instant.now());
        when(sessions.findForUpdateByIdAndUserId(id, user.id())).thenReturn(Optional.of(session));
        UUID answerId = UUID.randomUUID();
        when(messages.findAnswerIdsByChatSessionId(id)).thenReturn(List.of(answerId));

        service.delete(user, id.toString());

        var ordered = inOrder(sessions, messages, answers, audit);
        ordered.verify(messages).findAnswerIdsByChatSessionId(id);
        ordered.verify(sessions).delete(session);
        ordered.verify(sessions).flush();
        ordered.verify(answers).deleteDisposableByIds(List.of(answerId));
        ordered.verify(audit).record(AuditAction.DELETE, "chat_session", id);
    }

    @Test
    void refusesMalformedMissingAndOtherUsersIdsWithoutChanges_AC_013_8() {
        when(sessions.findByIdAndUserId(id, user.id())).thenReturn(Optional.empty());
        when(sessions.findForUpdateByIdAndUserId(id, user.id())).thenReturn(Optional.empty());
        for (String value : List.of("not-a-uuid", id.toString())) {
            assertThatThrownBy(() -> service.get(user, value))
                    .isInstanceOf(ChatbotConversationNotFoundException.class)
                    .hasMessage("MSG04");
            assertThatThrownBy(() -> service.delete(user, value))
                    .isInstanceOf(ChatbotConversationNotFoundException.class)
                    .hasMessage("MSG04");
        }
        verifyNoInteractions(messages, answers, audit);
        verify(sessions, never()).delete(any());
    }

    @Test
    void rechecksExpiryBeforeDeletingAndUsesTheSameAnswerPolicy_AC_013_9() {
        Instant cutoff = Instant.parse("2026-10-19T00:00:00Z");
        when(sessions.findExpiredForUpdate(id, cutoff)).thenReturn(Optional.empty());
        assertThat(service.deleteExpired(id, cutoff)).isFalse();
        verifyNoInteractions(messages, answers, audit);

        var session = session(id, "Expired", cutoff.minusSeconds(1));
        when(sessions.findExpiredForUpdate(id, cutoff)).thenReturn(Optional.of(session));
        UUID answerId = UUID.randomUUID();
        when(messages.findAnswerIdsByChatSessionId(id)).thenReturn(List.of(answerId));
        assertThat(service.deleteExpired(id, cutoff)).isTrue();
        verify(sessions).delete(session);
        verify(sessions).flush();
        verify(answers).deleteDisposableByIds(List.of(answerId));
        // Recurring jobs have no signed-in actor.
        verifyNoInteractions(audit);
    }

    private ChatSession session(UUID sessionId, String title, Instant startedAt) {
        var session = mock(ChatSession.class);
        when(session.getId()).thenReturn(sessionId);
        when(session.getTitle()).thenReturn(title);
        when(session.getStartedAt()).thenReturn(startedAt);
        return session;
    }

    private ChatMessage message(UUID answerId, String question, String outcome, String text, String citations) {
        var message = mock(ChatMessage.class);
        when(message.getChatbotAnswerId()).thenReturn(answerId);
        when(message.getQuestion()).thenReturn(question);
        when(message.getOutcome()).thenReturn(outcome);
        when(message.getAnswerText()).thenReturn(text);
        when(message.getCitations()).thenReturn(citations);
        return message;
    }
}
