package vn.tsolve.chatbot.conversation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.AdditionalAnswers.delegatesTo;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import vn.tsolve.chatbot.answer.repository.ChatbotAnswerRepository;
import vn.tsolve.chatbot.conversation.service.ChatbotConversationService;
import vn.tsolve.chatbot.conversation.service.impl.ConversationRetentionJob;
import vn.tsolve.common.audit.AuditService;
import vn.tsolve.common.tenant.ThreadLocalTenantContext;
import vn.tsolve.platform.service.WorkspaceCatalog;

class ConversationRetentionJobIT extends ConversationDatabaseITSupport {
    private static final Instant NOW = Instant.parse("2026-11-18T00:00:00Z");
    private static final Instant CUTOFF = NOW.minusSeconds(30 * 86400);

    @Test
    void expiresOnlyIdleConversationsAndAppliesTheAnswerPolicy_AC_013_9() {
        UUID expired = session(owner, "Expired", CUTOFF.minusSeconds(100), CUTOFF.minusSeconds(1));
        UUID atBoundary = session(owner, "Boundary", CUTOFF.minusSeconds(100), CUTOFF);
        UUID active = session(other, "Recently used", CUTOFF.minusSeconds(100), CUTOFF.plusSeconds(1));
        turn(expired, owner, "ANSWERED", null, 0, CUTOFF, "[]");
        UUID rated = turn(expired, owner, "ANSWERED", "NOT_HELPFUL", 1, CUTOFF, "[]");
        UUID noSource = turn(expired, owner, "NO_SOURCE", null, 2, CUTOFF, "[]");
        UUID injection = turn(expired, owner, "INJECTION", null, 3, CUTOFF, "[]");
        run(service -> assertThat(job(service).deleteExpiredConversations()).isEqualTo(1));
        assertThat(jdbc.queryForList("SELECT chat_session_id FROM chat_session", UUID.class))
                .containsExactlyInAnyOrder(atBoundary, active);
        assertThat(count("chat_message")).isZero();
        assertThat(jdbc.queryForList("SELECT chatbot_answer_id FROM chatbot_answer", UUID.class))
                .containsExactlyInAnyOrder(rated, noSource, injection);
    }

    @Test
    void rechecksLastMessageAtAfterCandidateSelection_AC_013_9() {
        UUID id = session(owner, "Refreshed", CUTOFF.minusSeconds(100), CUTOFF.minusSeconds(1));
        run(service -> {
            assertThat(service.findExpiredIds(CUTOFF)).containsExactly(id);
            jdbc.update(
                    "UPDATE chat_session SET last_message_at = ? WHERE chat_session_id = ?", Timestamp.from(NOW), id);
            assertThat(service.deleteExpired(id, CUTOFF)).isFalse();
        });
        assertThat(count("chat_session")).isEqualTo(1);
    }

    @Test
    void rollsBackOneFailureAndContinuesInSeparateTransactions_AC_013_9() {
        UUID failing = session(owner, "Failing", CUTOFF, CUTOFF.minusSeconds(1));
        UUID successful = session(other, "Successful", CUTOFF, CUTOFF.minusSeconds(1));
        UUID failedAnswer = turn(failing, owner, "ANSWERED", null, 0, CUTOFF, "[]");
        turn(successful, other, "ANSWERED", null, 0, CUTOFF, "[]");
        var repository = mock(ChatbotAnswerRepository.class, delegatesTo(answers));
        doThrow(new IllegalStateException("delete failure"))
                .when(repository)
                .deleteDisposableByIds(argThat(ids -> ids.contains(failedAnswer)));
        run(repository, mock(AuditService.class), service -> assertThat(
                        job(service).deleteExpiredConversations())
                .isEqualTo(1));
        assertThat(jdbc.queryForList("SELECT chat_session_id FROM chat_session", UUID.class))
                .containsExactly(failing);
        assertThat(jdbc.queryForList("SELECT chatbot_answer_id FROM chatbot_answer", UUID.class))
                .containsExactly(failedAnswer);
        assertThat(count("chat_message")).isEqualTo(1);
    }

    /** No catalog bean: one run, unbound, in this test's own database. */
    private static ConversationRetentionJob job(ChatbotConversationService service) {
        return new ConversationRetentionJob(
                service,
                Clock.fixed(NOW, ZoneOffset.UTC),
                "30d",
                new StaticListableBeanFactory().getBeanProvider(WorkspaceCatalog.class),
                new ThreadLocalTenantContext(id -> true));
    }
}
