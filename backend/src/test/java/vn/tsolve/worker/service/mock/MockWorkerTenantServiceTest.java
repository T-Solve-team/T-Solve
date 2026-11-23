package vn.tsolve.worker.service.mock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import vn.tsolve.common.tenant.ThreadLocalTenantContext;

/** The mock keeps no binding of its own: {@code runIn} and {@code currentWorkspaceId} go through the holder (6a-1). */
class MockWorkerTenantServiceTest {
    private static final UUID A = UUID.fromString("00000000-0000-4000-8000-00000000000a");
    private static final UUID B = UUID.fromString("00000000-0000-4000-8000-00000000000b");
    private static final UUID S = UUID.fromString("00000000-0000-4000-8000-00000000000c");
    private static final UUID KNOWN_ONLY = UUID.fromString("00000000-0000-4000-8000-00000000000d");

    // Every id the tests bind is known to the holder; the mock's own lists are what each test passes in.
    private final ThreadLocalTenantContext holder = new ThreadLocalTenantContext(Set.of(A, B, S, KNOWN_ONLY)::contains);

    @AfterEach
    void clearRequestAndBinding() {
        RequestContextHolder.resetRequestAttributes();
        assertThat(holder.currentWorkspaceId()).as("binding left on the thread").isEmpty();
    }

    @Test
    void listsActiveWorkspacesWithoutBinding_AC_003_1() {
        var tenants = new MockWorkerTenantService(Set.of(A), Set.of(), holder);
        assertThat(tenants.currentWorkspaceId()).isEmpty();
        assertThat(tenants.activeWorkspaceIds()).containsExactly(A);
        assertThat(tenants.currentWorkspaceId()).isEmpty();
        tenants.runIn(A, () -> {
            assertThat(tenants.activeWorkspaceIds()).containsExactly(A);
            assertThat(tenants.currentWorkspaceId()).contains(A);
        });
        assertThat(tenants.currentWorkspaceId()).isEmpty();
    }

    @Test
    void returnsOnlyTheImmutableActiveAllowList_AC_003_2() {
        UUID unknown = UUID.randomUUID();
        var configured = new HashSet<>(Set.of(A));
        var tenants = new MockWorkerTenantService(configured, Set.of(S), holder);
        configured.add(unknown);
        assertThat(tenants.activeWorkspaceIds()).containsExactly(A).doesNotContain(S, unknown);
        assertThatThrownBy(() -> tenants.activeWorkspaceIds().add(unknown))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThat(new MockWorkerTenantService(Set.of(), Set.of(S), holder).activeWorkspaceIds())
                .isEmpty();
    }

    @Test
    @DisplayName(
            "AC-002.12 runIn and currentWorkspaceId use the holder, and enqueueWorkspaceId on an unbound non-request thread never throws")
    void usesTheHolderAndNeverThrowsOnAnUnboundThread_AC_002_12() {
        var tenants = new MockWorkerTenantService(Set.of(A, B), Set.of(), holder);

        AtomicReference<Optional<UUID>> holderSees = new AtomicReference<>();
        tenants.runIn(A, () -> holderSees.set(holder.currentWorkspaceId()));
        assertThat(holderSees.get()).contains(A);
        assertThat(holder.currentWorkspaceId()).isEmpty();

        AtomicReference<Optional<UUID>> mockSees = new AtomicReference<>();
        holder.runIn(B, () -> mockSees.set(tenants.currentWorkspaceId()));
        assertThat(mockSees.get()).contains(B);

        // No binding, no request attributes (a scheduler or executor thread): never throws, never guesses.
        assertThatCode(tenants::enqueueWorkspaceId).doesNotThrowAnyException();
        assertThat(tenants.enqueueWorkspaceId()).as("two Active Workspaces").isEmpty();
        assertThat(new MockWorkerTenantService(Set.of(A), Set.of(), holder).enqueueWorkspaceId())
                .as("one Active Workspace")
                .contains(A);
    }

    @Test
    @DisplayName(
            "AC-002.14 the mock still refuses a Workspace outside its own Active and Suspended lists, even one the holder knows")
    void keepsItsOwnUnknownWorkspaceCheck_AC_002_14() {
        // Q3 (a): no behaviour is removed in 6a-1, so a job needs both lists to agree.
        var tenants = new MockWorkerTenantService(Set.of(A), Set.of(S), holder);
        AtomicInteger ran = new AtomicInteger();

        assertThatThrownBy(() -> tenants.runIn(KNOWN_ONLY, ran::incrementAndGet))
                .isInstanceOf(RuntimeException.class);

        assertThat(ran).hasValue(0);
        tenants.runIn(S, ran::incrementAndGet);
        assertThat(ran)
                .as("a Suspended Workspace in the mock list still binds (AC-002.15)")
                .hasValue(1);
    }

    @Test
    @DisplayName("AC-002.12 a bound Active Workspace is returned with and without a request attribute")
    void enqueueWorkspaceTakesTheBoundActiveWorkspaceOnAnyThread_AC_002_12() {
        var tenants = new MockWorkerTenantService(Set.of(A, B), Set.of(S), holder);
        AtomicReference<Optional<UUID>> job = new AtomicReference<>();
        holder.runIn(A, () -> job.set(tenants.enqueueWorkspaceId()));
        assertThat(job.get()).as("a job thread, no request").contains(A);

        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(new MockHttpServletRequest()));
        AtomicReference<Optional<UUID>> request = new AtomicReference<>();
        holder.runIn(A, () -> request.set(tenants.enqueueWorkspaceId()));
        assertThat(request.get()).as("a request thread").contains(A);
    }

    @Test
    @DisplayName("edge 4 a bound Suspended Workspace yields an empty enqueueWorkspaceId on any thread, request or not")
    void enqueueWorkspaceQueuesNothingForABoundSuspendedWorkspaceOnAnyThread_edge_4() {
        var tenants = new MockWorkerTenantService(Set.of(A, B), Set.of(S), holder);
        AtomicReference<Optional<UUID>> chosen = new AtomicReference<>();

        holder.runIn(S, () -> chosen.set(tenants.enqueueWorkspaceId()));

        assertThat(chosen.get())
                .as("the worker would refuse a Suspended Workspace's job")
                .isEmpty();
    }

    @Test
    @DisplayName("edge 4 a bound Workspace outside the mock lists yields an empty enqueueWorkspaceId on any thread")
    void enqueueWorkspaceQueuesNothingForABoundWorkspaceOutsideTheMockLists_edge_4() {
        var tenants = new MockWorkerTenantService(Set.of(A, B), Set.of(S), holder);
        AtomicReference<Optional<UUID>> chosen = new AtomicReference<>();

        // KNOWN_ONLY is known to the holder, so it binds, but the mock lists do not name it.
        holder.runIn(KNOWN_ONLY, () -> chosen.set(tenants.enqueueWorkspaceId()));

        assertThat(chosen.get()).as("an unlisted Workspace is never queued").isEmpty();
    }

    @Test
    @DisplayName("AC-002.12 the constructor that made its own holder is gone, the holder is always passed in")
    void hasOnlyTheConstructorThatTakesTheHolder_AC_002_12() {
        assertThat(MockWorkerTenantService.class.getConstructors())
                .singleElement()
                .satisfies(constructor -> assertThat(constructor.getParameterCount())
                        .as("active, suspended, holder")
                        .isEqualTo(3));
    }

    @Test
    @DisplayName("AC-002.16 with nothing bound enqueueWorkspaceId is the single Active Workspace, else empty")
    void enqueueWorkspaceWithNothingBoundIsTheSingleActiveOneElseEmpty_AC_002_16() {
        assertThat(new MockWorkerTenantService(Set.of(A), Set.of(S), holder).enqueueWorkspaceId())
                .contains(A);
        assertThat(new MockWorkerTenantService(Set.of(A, B), Set.of(S), holder).enqueueWorkspaceId())
                .isEmpty();
        assertThat(new MockWorkerTenantService(Set.of(), Set.of(S), holder).enqueueWorkspaceId())
                .isEmpty();
    }
}
