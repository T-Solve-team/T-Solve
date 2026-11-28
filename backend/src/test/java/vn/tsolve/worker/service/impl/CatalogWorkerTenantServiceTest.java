package vn.tsolve.worker.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import vn.tsolve.common.tenant.ThreadLocalTenantContext;
import vn.tsolve.platform.dto.WorkspaceStatus;
import vn.tsolve.platform.dto.WorkspaceSummary;
import vn.tsolve.platform.service.WorkspaceCatalog;

/**
 * The worker's {@code WorkerTenantService} over the Registry catalog (Task 4c). A is Active, B is Active, S is
 * Suspended, X is unknown to the catalog. Worker ids only: a platform id here would count for the worker's criterion.
 */
class CatalogWorkerTenantServiceTest {
    private static final UUID A = UUID.fromString("00000000-0000-4000-8000-00000000000a");
    private static final UUID B = UUID.fromString("00000000-0000-4000-8000-00000000000b");
    private static final UUID S = UUID.fromString("00000000-0000-4000-8000-00000000000c");
    private static final UUID X = UUID.fromString("00000000-0000-4000-8000-00000000000d");

    private WorkspaceCatalog catalog;
    /** The rule of {@code SignInConfig.knownWorkspaces}: a Workspace is known when the catalog has its row. */
    private ThreadLocalTenantContext holder;
    /** For a Workspace the catalog does not know (a row removed between bind and enqueue). */
    private ThreadLocalTenantContext anyIdHolder;

    private CatalogWorkerTenantService service;

    @BeforeEach
    void setUp() {
        catalog = mock(WorkspaceCatalog.class);
        when(catalog.findById(A)).thenReturn(Optional.of(summary(A, WorkspaceStatus.ACTIVE)));
        when(catalog.findById(B)).thenReturn(Optional.of(summary(B, WorkspaceStatus.ACTIVE)));
        when(catalog.findById(S)).thenReturn(Optional.of(summary(S, WorkspaceStatus.SUSPENDED)));
        holder = new ThreadLocalTenantContext(id -> catalog.findById(id).isPresent());
        anyIdHolder = new ThreadLocalTenantContext(id -> true);
        service = new CatalogWorkerTenantService(catalog, holder);
    }

    @AfterEach
    void clearRequestAndBinding() {
        RequestContextHolder.resetRequestAttributes();
        assertThat(holder.currentWorkspaceId()).as("binding left on the thread").isEmpty();
    }

    private static WorkspaceSummary summary(UUID id, WorkspaceStatus status) {
        return new WorkspaceSummary(
                id, "ws-" + id, status, "example.test", Optional.empty(), Optional.empty(), Optional.empty());
    }

    private static void onARequestThread() {
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(new MockHttpServletRequest()));
    }

    @Test
    @DisplayName(
            "AC-003.1 activeWorkspaceIds is the catalog's Active list, empty when none is Active, and binds nothing")
    void activeWorkspaceIdsIsTheCatalogsActiveList_AC_003_1() {
        when(catalog.findActive())
                .thenReturn(List.of(summary(A, WorkspaceStatus.ACTIVE), summary(B, WorkspaceStatus.ACTIVE)));
        assertThat(service.currentWorkspaceId()).isEmpty();
        assertThat(service.activeWorkspaceIds()).containsExactlyInAnyOrder(A, B);
        assertThat(service.currentWorkspaceId()).isEmpty();
        verify(catalog, never()).findById(any());

        when(catalog.findActive()).thenReturn(List.of());
        assertThat(service.activeWorkspaceIds()).isEmpty();

        when(catalog.findActive())
                .thenReturn(List.of(summary(A, WorkspaceStatus.ACTIVE), summary(B, WorkspaceStatus.ACTIVE)));
        holder.runIn(A, () -> {
            assertThat(service.activeWorkspaceIds())
                    .as("the port does not filter by the binding")
                    .containsExactlyInAnyOrder(A, B);
            assertThat(holder.currentWorkspaceId()).contains(A);
        });
    }

    @Test
    @DisplayName(
            "AC-003.3 edge 84 a Registry failure while listing the Active Workspaces propagates, never as an empty list")
    void activeListFailurePropagates_AC_003_3_edge84() {
        var failure = new DataAccessResourceFailureException("registry down");
        when(catalog.findActive()).thenThrow(failure);

        assertThatThrownBy(() -> service.activeWorkspaceIds()).isSameAs(failure);
        // Q2: enqueueWorkspaceId propagates too, so an outage never looks like "no Workspace".
        assertThatThrownBy(() -> service.enqueueWorkspaceId()).isSameAs(failure);

        when(catalog.findById(A)).thenThrow(failure);
        var boundService = new CatalogWorkerTenantService(catalog, anyIdHolder);
        assertThatThrownBy(() -> anyIdHolder.runIn(A, () -> boundService.enqueueWorkspaceId()))
                .isSameAs(failure);
    }

    @Test
    @DisplayName(
            "AC-002.4 AC-002.5 isWorkspaceActive is true only for an Active Workspace, and a Registry failure propagates")
    void isWorkspaceActiveOnlyForActive_AC_002_4_AC_002_5() {
        assertThat(service.isWorkspaceActive(A)).isTrue();
        assertThat(service.isWorkspaceActive(S)).as("Suspended").isFalse();
        assertThat(service.isWorkspaceActive(X)).as("unknown to the catalog").isFalse();
        assertThatThrownBy(() -> service.isWorkspaceActive(null)).isInstanceOf(NullPointerException.class);

        var failure = new DataAccessResourceFailureException("registry down");
        when(catalog.findById(B)).thenThrow(failure);
        assertThatThrownBy(() -> service.isWorkspaceActive(B)).isSameAs(failure);
    }

    @Test
    @DisplayName(
            "AC-002.1 BUG-324 enqueueWorkspaceId is the bound Active Workspace on a request thread and on a plain thread")
    void boundActiveWorkspaceOnAnyThread_AC_002_1_BUG_324() {
        when(catalog.findActive())
                .thenReturn(List.of(summary(A, WorkspaceStatus.ACTIVE), summary(B, WorkspaceStatus.ACTIVE)));
        AtomicReference<Optional<UUID>> plain = new AtomicReference<>();
        holder.runIn(B, () -> plain.set(service.enqueueWorkspaceId()));
        assertThat(plain.get())
                .as("a plain (job) thread, two Active Workspaces")
                .contains(B);

        onARequestThread();
        AtomicReference<Optional<UUID>> request = new AtomicReference<>();
        holder.runIn(A, () -> request.set(service.enqueueWorkspaceId()));
        assertThat(request.get()).as("a request thread").contains(A);
    }

    @Test
    @DisplayName(
            "edge 82 a bound Suspended Workspace gives an empty enqueueWorkspaceId on any thread and never the single Active one")
    void boundSuspendedIsEmptyOnAnyThread_edge82() {
        when(catalog.findActive()).thenReturn(List.of(summary(A, WorkspaceStatus.ACTIVE)));
        AtomicReference<Optional<UUID>> plain = new AtomicReference<>();
        holder.runIn(S, () -> plain.set(service.enqueueWorkspaceId()));
        assertThat(plain.get()).as("plain thread").isEmpty();

        onARequestThread();
        AtomicReference<Optional<UUID>> request = new AtomicReference<>();
        holder.runIn(S, () -> request.set(service.enqueueWorkspaceId()));
        assertThat(request.get()).as("request thread").isEmpty();
    }

    @Test
    @DisplayName(
            "edge 82 a bound Workspace the catalog does not know gives an empty enqueueWorkspaceId, never the single Active one")
    void boundUnknownIsEmpty_edge82() {
        when(catalog.findActive()).thenReturn(List.of(summary(A, WorkspaceStatus.ACTIVE)));
        var unknownBoundService = new CatalogWorkerTenantService(catalog, anyIdHolder);
        AtomicReference<Optional<UUID>> chosen = new AtomicReference<>();

        anyIdHolder.runIn(X, () -> chosen.set(unknownBoundService.enqueueWorkspaceId()));

        assertThat(chosen.get()).as("a row removed between bind and enqueue").isEmpty();
    }

    @Test
    @DisplayName("BUG-164 with nothing bound enqueueWorkspaceId is the single Active Workspace and binds nothing")
    void unboundSingleActive_BUG_164() {
        when(catalog.findActive()).thenReturn(List.of(summary(A, WorkspaceStatus.ACTIVE)));

        assertThat(service.enqueueWorkspaceId()).contains(A);
        assertThat(service.currentWorkspaceId()).isEmpty();
    }

    @Test
    @DisplayName(
            "BUG-164 with nothing bound and no Active Workspace, or two, enqueueWorkspaceId is empty and never guesses")
    void unboundNoneOrSeveralIsEmpty_BUG_164() {
        when(catalog.findActive()).thenReturn(List.of());
        assertThat(service.enqueueWorkspaceId()).as("none Active").isEmpty();

        when(catalog.findActive())
                .thenReturn(List.of(summary(A, WorkspaceStatus.ACTIVE), summary(B, WorkspaceStatus.ACTIVE)));
        assertThat(service.enqueueWorkspaceId()).as("two Active").isEmpty();
    }

    @Test
    @DisplayName(
            "AC-002.6 AC-002.7 runIn goes through the holder: an unknown id is refused without running, a Suspended one binds, a different bound Workspace is refused and the binding is restored")
    void runInGoesThroughTheHolder_AC_002_6_AC_002_7() {
        AtomicInteger ran = new AtomicInteger();

        assertThatThrownBy(() -> service.runIn(X, ran::incrementAndGet)).isInstanceOf(IllegalArgumentException.class);
        assertThat(ran).as("an unknown Workspace never runs").hasValue(0);

        AtomicReference<Optional<UUID>> seen = new AtomicReference<>();
        service.runIn(S, () -> seen.set(holder.currentWorkspaceId()));
        assertThat(seen.get()).as("a Suspended Workspace still binds").contains(S);
        assertThat(holder.currentWorkspaceId()).isEmpty();

        holder.runIn(A, () -> {
            assertThatThrownBy(() -> service.runIn(B, ran::incrementAndGet)).isInstanceOf(IllegalStateException.class);
            assertThat(ran).as("a different bound Workspace is refused").hasValue(0);
            assertThat(holder.currentWorkspaceId()).as("binding restored").contains(A);
        });

        var failure = new IllegalStateException("job failed");
        assertThatThrownBy(() -> service.runIn(A, () -> {
                    throw failure;
                }))
                .isSameAs(failure);
        assertThat(holder.currentWorkspaceId())
                .as("nothing left bound after a failure")
                .isEmpty();

        verify(catalog, never()).findActive();
    }

    @Test
    @DisplayName("AC-002.3 currentWorkspaceId is the holder's binding and reads no catalog")
    void currentWorkspaceIdIsTheHolders_AC_002_3() {
        assertThat(service.currentWorkspaceId()).isEmpty();

        holder.runIn(A, () -> {
            clearInvocations(catalog); // the holder's own known-check has just called findById
            assertThat(service.currentWorkspaceId()).contains(A);
            verifyNoInteractions(catalog);
        });

        service.runIn(B, () -> {
            assertThat(holder.currentWorkspaceId()).contains(B);
            clearInvocations(catalog);
            assertThat(service.currentWorkspaceId()).contains(B);
            verifyNoInteractions(catalog);
        });

        assertThat(service.currentWorkspaceId()).isEmpty();
    }
}
