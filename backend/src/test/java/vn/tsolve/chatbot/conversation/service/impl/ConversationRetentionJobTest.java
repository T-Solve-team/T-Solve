package vn.tsolve.chatbot.conversation.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.scheduling.annotation.Scheduled;
import vn.tsolve.chatbot.conversation.service.ChatbotConversationService;
import vn.tsolve.common.tenant.TenantContext;
import vn.tsolve.common.tenant.ThreadLocalTenantContext;
import vn.tsolve.platform.dto.WorkspaceStatus;
import vn.tsolve.platform.dto.WorkspaceSummary;
import vn.tsolve.platform.service.WorkspaceCatalog;

@ExtendWith(OutputCaptureExtension.class)
class ConversationRetentionJobTest {
    private static final Instant NOW = Instant.parse("2026-11-18T00:00:00Z");
    private final ChatbotConversationService conversations = mock(ChatbotConversationService.class);
    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
    private final TenantContext tenants = new ThreadLocalTenantContext(id -> true);

    @Test
    void deletesExpiredConversationsUsingConfiguredRetention_AC_013_9() {
        Instant cutoff = NOW.minusSeconds(7 * 86400);
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        when(conversations.findExpiredIds(cutoff)).thenReturn(List.of(first, second));
        when(conversations.deleteExpired(first, cutoff)).thenReturn(true);
        when(conversations.deleteExpired(second, cutoff)).thenReturn(true);

        assertThat(job("7d", emptyProvider()).deleteExpiredConversations()).isEqualTo(2);
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

        assertThat(job("30d", emptyProvider()).deleteExpiredConversations()).isEqualTo(1);
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

    @Test
    void deletesInEachActiveWorkspaceBoundAndAFailingOneDoesNotStopTheNext_BUG_402(CapturedOutput output) {
        Instant cutoff = NOW.minusSeconds(30 * 86400);
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        UUID expired = UUID.randomUUID();
        WorkspaceCatalog catalog = mock(WorkspaceCatalog.class);
        when(catalog.findActive()).thenReturn(List.of(active(a), active(b)));
        when(conversations.findExpiredIds(cutoff)).thenAnswer(call -> {
            UUID bound = tenants.currentWorkspaceId().orElse(null);
            if (a.equals(bound)) {
                throw new IllegalStateException("PRIVATE QUESTION ANSWER CITATION");
            }
            return b.equals(bound) ? List.of(expired) : List.of();
        });
        List<Optional<UUID>> deletedIn = new ArrayList<>();
        when(conversations.deleteExpired(expired, cutoff))
                .thenAnswer(call -> deletedIn.add(tenants.currentWorkspaceId()));

        job("30d", providerWith(catalog)).deleteExpiredConversations();

        assertThat(deletedIn).containsExactly(Optional.of(b));
        assertThat(output.getAll())
                .contains("IllegalStateException")
                .doesNotContain(a.toString(), b.toString(), "PRIVATE QUESTION ANSWER CITATION");
        verify(catalog).findActive();
        verifyNoMoreInteractions(catalog);
        assertThat(tenants.currentWorkspaceId()).isEmpty();
    }

    @Test
    void runsOnceUnboundWithoutACatalogAndACatalogFailurePropagates_BUG_402() {
        Instant cutoff = NOW.minusSeconds(30 * 86400);
        UUID expired = UUID.randomUUID();
        List<Optional<UUID>> bound = new ArrayList<>();
        when(conversations.findExpiredIds(cutoff)).thenAnswer(call -> {
            bound.add(tenants.currentWorkspaceId());
            return List.of(expired);
        });
        when(conversations.deleteExpired(expired, cutoff)).thenAnswer(call -> bound.add(tenants.currentWorkspaceId()));

        job("30d", emptyProvider()).deleteExpiredConversations();

        verify(conversations).findExpiredIds(cutoff);
        verify(conversations).deleteExpired(expired, cutoff);
        assertThat(bound).containsExactly(Optional.empty(), Optional.empty());

        WorkspaceCatalog catalog = mock(WorkspaceCatalog.class);
        var failure = new DataAccessResourceFailureException("registry down");
        when(catalog.findActive()).thenThrow(failure);
        ChatbotConversationService untouched = mock(ChatbotConversationService.class);
        ConversationRetentionJob job =
                new ConversationRetentionJob(untouched, clock, "30d", providerWith(catalog), tenants);

        assertThatThrownBy(job::deleteExpiredConversations).isSameAs(failure);
        verifyNoInteractions(untouched);
        assertThat(tenants.currentWorkspaceId()).isEmpty();
    }

    private ConversationRetentionJob job(String retention, ObjectProvider<WorkspaceCatalog> catalog) {
        return new ConversationRetentionJob(conversations, clock, retention, catalog, tenants);
    }

    private static WorkspaceSummary active(UUID id) {
        return new WorkspaceSummary(
                id,
                "site",
                WorkspaceStatus.ACTIVE,
                "site.example",
                Optional.empty(),
                Optional.empty(),
                Optional.empty());
    }

    private static ObjectProvider<WorkspaceCatalog> providerWith(WorkspaceCatalog catalog) {
        return new StaticListableBeanFactory(Map.of("workspaceCatalog", catalog))
                .getBeanProvider(WorkspaceCatalog.class);
    }

    private static ObjectProvider<WorkspaceCatalog> emptyProvider() {
        return new StaticListableBeanFactory().getBeanProvider(WorkspaceCatalog.class);
    }
}
