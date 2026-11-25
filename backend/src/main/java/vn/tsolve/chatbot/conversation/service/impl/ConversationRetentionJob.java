package vn.tsolve.chatbot.conversation.service.impl;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.convert.DurationStyle;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import vn.tsolve.chatbot.conversation.service.ChatbotConversationService;

@Component
public class ConversationRetentionJob {
    private static final Logger log = LoggerFactory.getLogger(ConversationRetentionJob.class);
    private final ChatbotConversationService conversations;
    private final Clock clock;
    private final Duration retention;

    public ConversationRetentionJob(
            ChatbotConversationService conversations,
            Clock clock,
            @Value("${CHATBOT_CONVERSATION_RETENTION:30d}") String retention) {
        this.conversations = conversations;
        this.clock = clock;
        this.retention = DurationStyle.detectAndParse(retention);
    }

    // ponytail: runs in the process's database until worker runs recurring jobs per Workspace.
    @Scheduled(cron = "${CHATBOT_CONVERSATION_CLEANUP_CRON:0 0 2 * * *}", zone = "Asia/Ho_Chi_Minh")
    public int deleteExpiredConversations() {
        Instant cutoff = clock.instant().minus(retention);
        int deleted = 0;
        for (var id : conversations.findExpiredIds(cutoff)) {
            try {
                if (conversations.deleteExpired(id, cutoff)) {
                    deleted++;
                }
            } catch (RuntimeException failure) {
                // Exceptions may contain SQL parameters, including conversation content.
                log.warn("Could not delete expired chatbot conversation {}", id);
            }
        }
        return deleted;
    }
}
