package vn.tsolve.ticket.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
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
import vn.tsolve.common.tenant.TenantContext;
import vn.tsolve.common.tenant.ThreadLocalTenantContext;
import vn.tsolve.platform.dto.WorkspaceStatus;
import vn.tsolve.platform.dto.WorkspaceSummary;
import vn.tsolve.platform.service.WorkspaceCatalog;
import vn.tsolve.ticket.repository.ImportPreviewRepository;

@ExtendWith(OutputCaptureExtension.class)
class PreviewCleanupJobTest {

    private static final Instant NOW = Instant.parse("2026-10-22T18:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneId.of("Asia/Ho_Chi_Minh"));

    private final ImportPreviewRepository previews = mock(ImportPreviewRepository.class);
    private final TenantContext tenants = new ThreadLocalTenantContext(id -> true);
    private final PreviewCleanupJob job = new PreviewCleanupJob(previews, CLOCK, "", emptyProvider(), tenants);

    @Test
    void deletesEveryPreviewOlderThanTwentyFourHours_AC_006_1() {
        when(previews.findIdsCreatedBefore(NOW.minusSeconds(24 * 3600))).thenReturn(List.of(3L, 5L));

        int deleted = job.deleteExpiredPreviews();

        assertThat(deleted).isEqualTo(2);
        verify(previews).deleteById(3L);
        verify(previews).deleteById(5L);
    }

    @Test
    void keepsPreviewsYoungerThanTwentyFourHours_AC_006_2() {
        // the cut-off is exactly now minus 24 h and the query is "created before" it, so a younger preview is kept
        when(previews.findIdsCreatedBefore(NOW.minusSeconds(24 * 3600))).thenReturn(List.of());

        assertThat(job.deleteExpiredPreviews()).isZero();
        verify(previews).findIdsCreatedBefore(NOW.minusSeconds(24 * 3600));
    }

    @Test
    void keepsGoingWhenOneDeletionFails_AC_006_3() {
        when(previews.findIdsCreatedBefore(NOW.minusSeconds(24 * 3600))).thenReturn(List.of(3L, 4L, 5L));
        doThrow(new DataAccessResourceFailureException("lock timeout"))
                .when(previews)
                .deleteById(4L);

        int deleted = job.deleteExpiredPreviews();

        assertThat(deleted).isEqualTo(2);
        verify(previews).deleteById(3L);
        verify(previews).deleteById(5L);
    }

    @Test
    void runsDailyAtOneInVietnamByDefault_AC_006_1() {
        assertThat(job.trigger().getExpression()).isEqualTo("0 0 1 * * *");
        assertThat(PreviewCleanupJob.ZONE).isEqualTo(ZoneId.of("Asia/Ho_Chi_Minh"));
    }

    @Test
    void takesTheRunTimeFromTheEnvironment_AC_006_1() {
        PreviewCleanupJob configured = new PreviewCleanupJob(previews, CLOCK, " 30 2 * * * ", emptyProvider(), tenants);

        assertThat(configured.trigger().getExpression()).isEqualTo("0 30 2 * * *");
    }

    @Test
    void refusesToStartWithAnInvalidCronNamingTheVariable_AC_006_1() {
        assertThatThrownBy(() -> new PreviewCleanupJob(previews, CLOCK, "1am", emptyProvider(), tenants))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("IMPORT_PREVIEW_CLEANUP_CRON");
    }

    @Test
    void deletesInEachActiveWorkspaceBoundAndSkipsOneThatCannotBeBound_BUG_402(CapturedOutput output) {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        // the dev profile: runIn refuses an id that DevWorkspaceLookup does not know
        TenantContext refusingA = new ThreadLocalTenantContext(id -> !id.equals(a));
        WorkspaceCatalog catalog = mock(WorkspaceCatalog.class);
        when(catalog.findActive()).thenReturn(List.of(active(a), active(b)));
        List<Optional<UUID>> searchedIn = new ArrayList<>();
        List<Optional<UUID>> deletedIn = new ArrayList<>();
        when(previews.findIdsCreatedBefore(NOW.minusSeconds(24 * 3600))).thenAnswer(call -> {
            searchedIn.add(refusingA.currentWorkspaceId());
            return List.of(3L);
        });
        doAnswer(call -> deletedIn.add(refusingA.currentWorkspaceId()))
                .when(previews)
                .deleteById(3L);

        new PreviewCleanupJob(previews, CLOCK, "", providerWith(catalog), refusingA).deleteExpiredPreviews();

        assertThat(searchedIn).containsExactly(Optional.of(b));
        assertThat(deletedIn).containsExactly(Optional.of(b));
        assertThat(output.getAll())
                .contains("IllegalArgumentException")
                .doesNotContain(a.toString(), b.toString(), "the Workspace is not known");
        verify(catalog).findActive();
        verifyNoMoreInteractions(catalog);
        assertThat(refusingA.currentWorkspaceId()).isEmpty();
    }

    @Test
    void runsOnceUnboundWithoutACatalogAndACatalogFailurePropagates_BUG_402() {
        List<Optional<UUID>> bound = new ArrayList<>();
        when(previews.findIdsCreatedBefore(NOW.minusSeconds(24 * 3600))).thenAnswer(call -> {
            bound.add(tenants.currentWorkspaceId());
            return List.of(3L);
        });
        doAnswer(call -> bound.add(tenants.currentWorkspaceId())).when(previews).deleteById(3L);

        job.deleteExpiredPreviews();

        verify(previews).findIdsCreatedBefore(NOW.minusSeconds(24 * 3600));
        verify(previews).deleteById(3L);
        assertThat(bound).containsExactly(Optional.empty(), Optional.empty());

        WorkspaceCatalog catalog = mock(WorkspaceCatalog.class);
        var failure = new DataAccessResourceFailureException("registry down");
        when(catalog.findActive()).thenThrow(failure);
        ImportPreviewRepository untouched = mock(ImportPreviewRepository.class);
        PreviewCleanupJob failing = new PreviewCleanupJob(untouched, CLOCK, "", providerWith(catalog), tenants);

        assertThatThrownBy(failing::deleteExpiredPreviews).isSameAs(failure);
        verifyNoInteractions(untouched);
        assertThat(tenants.currentWorkspaceId()).isEmpty();
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
