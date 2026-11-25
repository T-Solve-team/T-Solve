package vn.tsolve.chatbot.conversation.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.scheduling.annotation.Scheduled;
import vn.tsolve.chatbot.conversation.service.ChatbotConversationService;

@ExtendWith(OutputCaptureExtension.class)
class ConversationRetentionJobTest {
    private static final Instant NOW = Instant.parse("2026-11-18T00:00:00Z");
    private final ChatbotConversationService conversations = mock(ChatbotConversationService.class);
    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);

    @Test
    void deletesExpiredConversationsUsingConfiguredRetention_AC_013_9() {
        Instant cutoff = NOW.minusSeconds(7 * 86400);
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        when(conversations.findExpiredIds(cutoff)).thenReturn(List.of(first, second));
        when(conversations.deleteExpired(first, cutoff)).thenReturn(true);
        when(conversations.deleteExpired(second, cutoff)).thenReturn(true);

        assertThat(new ConversationRetentionJob(conversations, clock, "7d").deleteExpiredConversations())
                .isEqualTo(2);
        verify(conversations).deleteExpired(first, cutoff);
        verify(conversations).deleteExpired(second, cutoff);
    }

    @Test
    void continuesAfterFailureAndLogsOnlyTheId_AC_013_9_AC_013_10(CapturedOutput output) {
        Instant cutoff = NOW.minusSeconds(30 * 86400);
        UUID failing = UUID.randomUUID();
        UUID refreshed = UUID.randomUUID();
        UUID expired = UUID.randomUUID();
        when(conversations.findExpiredIds(cutoff)).thenReturn(List.of(failing, refreshed, expired));
        when(conversations.deleteExpired(failing, cutoff))
                .thenThrow(new IllegalStateException("PRIVATE QUESTION ANSWER CITATION"));
        when(conversations.deleteExpired(refreshed, cutoff)).thenReturn(false);
        when(conversations.deleteExpired(expired, cutoff)).thenReturn(true);

        assertThat(new ConversationRetentionJob(conversations, clock, "30d").deleteExpiredConversations())
                .isEqualTo(1);
        verify(conversations).deleteExpired(expired, cutoff);
        assertThat(output.getAll()).contains(failing.toString()).doesNotContain("PRIVATE QUESTION ANSWER CITATION");
    }

    @Test
    void schedulesDailyAtTwoInVietnamWithConfigurableCron_AC_013_9() throws Exception {
        var scheduled = ConversationRetentionJob.class
                .getMethod("deleteExpiredConversations")
                .getAnnotation(Scheduled.class);
        assertThat(scheduled).isNotNull();
        assertThat(scheduled.cron()).isEqualTo("${CHATBOT_CONVERSATION_CLEANUP_CRON:0 0 2 * * *}");
        assertThat(scheduled.zone()).isEqualTo("Asia/Ho_Chi_Minh");
    }
}
