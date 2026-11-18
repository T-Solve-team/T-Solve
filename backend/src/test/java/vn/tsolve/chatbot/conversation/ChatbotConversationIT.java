package vn.tsolve.chatbot.conversation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.data.domain.PageRequest;
import vn.tsolve.chatbot.answer.dto.ChatbotAnswerResponse.Outcome;
import vn.tsolve.chatbot.conversation.exception.ChatbotConversationNotFoundException;
import vn.tsolve.chatbot.rating.entity.ChatbotRating;
import vn.tsolve.common.audit.AuditAction;
import vn.tsolve.common.audit.AuditService;

@ExtendWith(OutputCaptureExtension.class)
class ChatbotConversationIT extends ConversationDatabaseITSupport {
    private static final Instant START = Instant.parse("2026-11-01T00:00:00Z");

    @Test
    void listsExactlyOwnConversationsByStartNotLastActivity_AC_013_5() {
        UUID older = session(owner, "Older", START, START.plusSeconds(300));
        UUID newer = session(owner, "Newer", START.plusSeconds(100), START.plusSeconds(100));
        session(other, "Other", START.plusSeconds(200), START.plusSeconds(200));
        run(service -> {
            var page = service.list(owner, PageRequest.of(0, 20));
            assertThat(page.getContent()).extracting("chatSessionId").containsExactly(newer, older);
            assertThat(page.getContent()).extracting("title").containsExactly("Newer", "Older");
            assertThat(page.getTotalElements()).isEqualTo(2);
        });
    }

    @Test
    void pagesOwnRowsTiesByIdAndEmptyPastTheEnd_AC_013_5() {
        var tied = new ArrayList<UUID>();
        for (int i = 0; i < 3; i++) tied.add(session(owner, "Tie " + i, START, START));
        UUID newest = session(owner, "Newest", START.plusSeconds(10), START.plusSeconds(10));
        session(other, "Other", START.plusSeconds(20), START.plusSeconds(20));
        var expected = new ArrayList<UUID>(tied);
        // PostgreSQL orders uuid bytewise, which is the order of the hex text (UUID.compareTo is signed).
        expected.sort(java.util.Comparator.comparing(UUID::toString).reversed());
        expected.addFirst(newest);
        run(service -> {
            var first = service.list(owner, PageRequest.of(0, 3));
            var second = service.list(owner, PageRequest.of(1, 3));
            assertThat(first.getContent())
                    .extracting("chatSessionId")
                    .containsExactlyElementsOf(expected.subList(0, 3));
            assertThat(second.getContent()).extracting("chatSessionId").containsExactly(expected.get(3));
            assertThat(first.getTotalElements()).isEqualTo(4);
            assertThat(first.getTotalPages()).isEqualTo(2);
            assertThat(service.list(owner, PageRequest.of(5, 3)).getContent()).isEmpty();
        });
    }

    @Test
    void opensAllTurnsWithStoredCitationsAndLiveRatings_AC_013_6() {
        UUID id = session(owner, "Title", START, START);
        UUID wiki = UUID.randomUUID();
        String citations = "[{\"type\":\"WIKI_DOCUMENT\",\"number\":1,\"wikiDocumentId\":\"" + wiki
                + "\",\"title\":\"Stored citation\"}]";
        var ids = new ArrayList<UUID>();
        for (int i = 11; i >= 0; i--) {
            ids.add(turn(
                    id,
                    owner,
                    i == 11 ? "NO_SOURCE" : "ANSWERED",
                    null,
                    i,
                    START.plusSeconds(i),
                    i == 0 ? citations : "[]"));
        }
        UUID firstAnswer = ids.getLast();
        run(service -> {
            var result = service.get(owner, id.toString());
            assertThat(result.turns()).hasSize(12);
            assertThat(result.turns())
                    .extracting("question")
                    .containsExactlyElementsOf(java.util.stream.IntStream.range(0, 12)
                            .mapToObj(i -> "Question " + i)
                            .toList());
            var first = result.turns().getFirst();
            assertThat(first.answer().answerId()).isEqualTo(firstAnswer);
            assertThat(first.answer().citations()).hasSize(1);
            assertThat(first.answer().chatSessionId()).isNull();
            assertThat(first.rating()).isNull();
            assertThat(result.turns().getLast().answer().outcome()).isEqualTo(Outcome.NO_SOURCE);
            assertThat(result.turns().getLast().answer().text()).isEmpty();
            jdbc.update(
                    "UPDATE chatbot_answer SET rating = 'HELPFUL', rated_at = now() WHERE chatbot_answer_id = ?",
                    firstAnswer);
            assertThat(service.get(owner, id.toString()).turns().getFirst().rating())
                    .isEqualTo(ChatbotRating.HELPFUL);
            jdbc.update(
                    "UPDATE chatbot_answer SET rating = NULL, rated_at = NULL WHERE chatbot_answer_id = ?",
                    firstAnswer);
            assertThat(service.get(owner, id.toString()).turns().getFirst().rating())
                    .isNull();
        });
    }

    @Test
    void deletesOnlyDisposableAnswersOfTheConversation_AC_013_7() {
        UUID id = session(owner, "Delete", START, START);
        var kept = new ArrayList<UUID>();
        for (String outcome : List.of("ANSWERED", "GREETING", "OFF_TOPIC", "NO_SOURCE", "INJECTION")) {
            UUID unrated = turn(id, owner, outcome, null, 0, START, "[]");
            if (List.of("NO_SOURCE", "INJECTION").contains(outcome)) {
                kept.add(unrated);
            }
            kept.add(turn(id, owner, outcome, "HELPFUL", 1, START, "[]"));
        }
        UUID otherSession = session(other, "Other", START, START);
        kept.add(turn(otherSession, other, "ANSWERED", null, 0, START, "[]"));
        UUID ownOtherSession = session(owner, "Keep", START, START);
        kept.add(turn(ownOtherSession, owner, "ANSWERED", null, 0, START, "[]"));
        run(service -> service.delete(owner, id.toString()));
        assertThat(jdbc.queryForList("SELECT chatbot_answer_id FROM chatbot_answer", UUID.class))
                .containsExactlyInAnyOrderElementsOf(kept);
        assertThat(count("chat_session")).isEqualTo(2);
        assertThat(count("chat_message")).isEqualTo(2);
    }

    @Test
    void unknownAndOtherUsersIdsChangeNothing_AC_013_8() {
        UUID id = session(other, "Other", START, START);
        turn(id, other, "ANSWERED", null, 0, START, "[]");
        run(service -> {
            for (String value : List.of("bad-id", UUID.randomUUID().toString(), id.toString())) {
                assertThatThrownBy(() -> service.get(owner, value))
                        .isInstanceOf(ChatbotConversationNotFoundException.class)
                        .hasMessage("MSG04");
                assertThatThrownBy(() -> service.delete(owner, value))
                        .isInstanceOf(ChatbotConversationNotFoundException.class)
                        .hasMessage("MSG04");
            }
        });
        assertThat(count("chat_session")).isEqualTo(1);
        assertThat(count("chat_message")).isEqualTo(1);
        assertThat(count("chatbot_answer")).isEqualTo(1);
    }

    @Test
    void deletionRollsBackIfAuditFails_AC_013_7() {
        UUID id = session(owner, "Rollback", START, START);
        turn(id, owner, "ANSWERED", null, 0, START, "[]");
        var audit = mock(AuditService.class);
        doThrow(new IllegalStateException("audit unavailable"))
                .when(audit)
                .record(eq(AuditAction.DELETE), any(), eq(id));
        run(answers, audit, service -> assertThatThrownBy(() -> service.delete(owner, id.toString()))
                .isInstanceOf(IllegalStateException.class));
        assertThat(count("chat_session")).isEqualTo(1);
        assertThat(count("chat_message")).isEqualTo(1);
        assertThat(count("chatbot_answer")).isEqualTo(1);
    }

    @Test
    void logsNoConversationContent_AC_013_10(CapturedOutput output) {
        UUID id = session(owner, "PRIVATE_TITLE_183", START, START);
        turn(
                id,
                owner,
                "ANSWERED",
                null,
                0,
                START,
                "[{\"type\":\"WIKI_DOCUMENT\",\"number\":1,\"wikiDocumentId\":\"" + UUID.randomUUID()
                        + "\",\"title\":\"PRIVATE_CITATION_183\"}]");
        jdbc.update("UPDATE chat_message SET question = 'PRIVATE_QUESTION_183', answer_text = 'PRIVATE_ANSWER_183'");
        run(service -> {
            service.list(owner, PageRequest.of(0, 20));
            service.get(owner, id.toString());
            service.delete(owner, id.toString());
        });
        assertThat(output.getAll())
                .doesNotContain(
                        "PRIVATE_TITLE_183", "PRIVATE_QUESTION_183", "PRIVATE_ANSWER_183", "PRIVATE_CITATION_183");
    }
}
