package vn.tsolve.search.embedding.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.AdditionalMatchers.aryEq;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.dao.DataAccessResourceFailureException;
import vn.tsolve.common.tenant.TenantContext;
import vn.tsolve.common.tenant.ThreadLocalTenantContext;
import vn.tsolve.platform.dto.WorkspaceStatus;
import vn.tsolve.platform.dto.WorkspaceSummary;
import vn.tsolve.platform.service.WorkspaceCatalog;
import vn.tsolve.search.config.SearchSchedulingConfig;
import vn.tsolve.search.embedding.repository.TicketEmbeddingRepository;
import vn.tsolve.search.embedding.service.TicketEmbeddingService;

@ExtendWith(OutputCaptureExtension.class)
class EmbeddingCatchUpJobTest {
    private final TenantContext tenants = new ThreadLocalTenantContext(id -> true);

    @Test
    void embedsLowestIdsWithoutVector_AC_005_7() {
        TicketEmbeddingRepository repository = mock(TicketEmbeddingRepository.class);
        TicketEmbeddingService service = mock(TicketEmbeddingService.class);
        when(service.skippedIds()).thenReturn(Set.of(new UUID(0, 1)));
        when(repository.findPublishedWithoutEmbedding(eq(4), aryEq(new UUID[] {new UUID(0, 1)})))
                .thenReturn(List.of(new UUID(0, 2), new UUID(0, 3)));

        EmbeddingCatchUpJob job = new EmbeddingCatchUpJob(repository, service, 4, emptyProvider(), tenants);
        job.run();

        verify(repository).findPublishedWithoutEmbedding(eq(4), aryEq(new UUID[] {new UUID(0, 1)}));
        verify(service).embed(List.of(new UUID(0, 2), new UUID(0, 3)));

        try (var context = new AnnotationConfigApplicationContext()) {
            context.register(SearchSchedulingConfig.class);
            context.registerBean(EmbeddingCatchUpJob.class, () -> job);
            context.refresh();
        }
    }

    @Test
    void runsOnceBoundPerActiveWorkspaceAndAFailingOneDoesNotStopTheNext_BUG_402(CapturedOutput output) {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        UUID c = UUID.randomUUID();
        UUID failingTicket = UUID.randomUUID();
        List<UUID> idsOfB = List.of(new UUID(0, 2));
        List<UUID> idsOfC = List.of(new UUID(0, 3));
        WorkspaceCatalog catalog = mock(WorkspaceCatalog.class);
        when(catalog.findActive()).thenReturn(List.of(active(a), active(b), active(c)));
        TicketEmbeddingRepository repository = mock(TicketEmbeddingRepository.class);
        when(repository.findPublishedWithoutEmbedding(eq(4), any())).thenAnswer(call -> {
            UUID bound = tenants.currentWorkspaceId().orElse(null);
            if (a.equals(bound)) {
                throw new IllegalStateException(failingTicket + " secret");
            }
            return b.equals(bound) ? idsOfB : c.equals(bound) ? idsOfC : List.of();
        });
        TicketEmbeddingService service = mock(TicketEmbeddingService.class);
        List<Map.Entry<Optional<UUID>, List<UUID>>> embedded = new ArrayList<>();
        doAnswer(call -> embedded.add(Map.entry(tenants.currentWorkspaceId(), call.getArgument(0))))
                .when(service)
                .embed(anyList());

        new EmbeddingCatchUpJob(repository, service, 4, providerWith(catalog), tenants).run();

        assertThat(embedded).containsExactly(Map.entry(Optional.of(b), idsOfB), Map.entry(Optional.of(c), idsOfC));
        assertThat(output.getAll())
                .contains("IllegalStateException")
                .doesNotContain(a.toString(), b.toString(), c.toString(), failingTicket.toString(), "secret");
        verify(catalog).findActive();
        verifyNoMoreInteractions(catalog);
        assertThat(tenants.currentWorkspaceId()).isEmpty();
    }

    @Test
    void runsOnceUnboundWithoutACatalogAndACatalogFailurePropagates_BUG_402() {
        List<UUID> ids = List.of(new UUID(0, 2));
        TicketEmbeddingRepository repository = mock(TicketEmbeddingRepository.class);
        TicketEmbeddingService service = mock(TicketEmbeddingService.class);
        List<Optional<UUID>> bound = new ArrayList<>();
        when(repository.findPublishedWithoutEmbedding(eq(4), any())).thenAnswer(call -> {
            bound.add(tenants.currentWorkspaceId());
            return ids;
        });
        doAnswer(call -> bound.add(tenants.currentWorkspaceId())).when(service).embed(ids);

        new EmbeddingCatchUpJob(repository, service, 4, emptyProvider(), tenants).run();

        verify(repository).findPublishedWithoutEmbedding(eq(4), any());
        verify(service).embed(ids);
        assertThat(bound).containsExactly(Optional.empty(), Optional.empty());

        WorkspaceCatalog catalog = mock(WorkspaceCatalog.class);
        var failure = new DataAccessResourceFailureException("registry down");
        when(catalog.findActive()).thenThrow(failure);
        TicketEmbeddingRepository untouched = mock(TicketEmbeddingRepository.class);
        EmbeddingCatchUpJob job = new EmbeddingCatchUpJob(untouched, service, 4, providerWith(catalog), tenants);

        assertThatThrownBy(job::run).isSameAs(failure);
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
