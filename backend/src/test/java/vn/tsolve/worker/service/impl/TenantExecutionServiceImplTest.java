package vn.tsolve.worker.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import vn.tsolve.worker.service.WorkerTenantService;
import vn.tsolve.worker.service.mock.MockWorkerTenantService;

class TenantExecutionServiceImplTest {
    private final UUID a = UUID.randomUUID();
    private final UUID b = UUID.randomUUID();
    private final UUID suspended = UUID.randomUUID();
    private final MockWorkerTenantService context = new MockWorkerTenantService(Set.of(a, b), Set.of(suspended));
    private final TenantExecutionServiceImpl execution = new TenantExecutionServiceImpl(context);

    @Test
    void bindsOnlyTheJobWorkspace_AC_001_3() {
        execution.runInTenant(a, () -> assertThat(context.currentWorkspaceId()).contains(a));
        assertThat(context.currentWorkspaceId()).isEmpty();
    }

    @Test
    void rejectsSuspendedUnknownAndNullWorkspace_AC_001_4_AC_001_11() {
        AtomicInteger calls = new AtomicInteger();
        for (UUID id : new UUID[] {suspended, UUID.randomUUID(), null}) {
            assertThatThrownBy(() -> execution.runInTenant(id, calls::incrementAndGet))
                    .isInstanceOf(RuntimeException.class);
        }
        assertThat(calls).hasValue(0);
        assertThat(context.currentWorkspaceId()).isEmpty();
    }

    @Test
    void failsClosedWhenCatalogIsUnavailable_AC_001_5() {
        WorkerTenantService unavailable = mock(WorkerTenantService.class);
        when(unavailable.isWorkspaceActive(a)).thenThrow(new IllegalStateException("catalog unavailable"));
        AtomicInteger calls = new AtomicInteger();
        assertThatThrownBy(() -> new TenantExecutionServiceImpl(unavailable).runInTenant(a, calls::incrementAndGet))
                .isInstanceOf(IllegalStateException.class);
        assertThat(calls).hasValue(0);
    }

    @Test
    void restoresContextAndPreservesOriginalFailure_AC_001_6() {
        var failure = new IllegalArgumentException("business failure");
        assertThatThrownBy(() -> execution.runInTenant(a, () -> {
                    throw failure;
                }))
                .isSameAs(failure);
        assertThat(context.currentWorkspaceId()).isEmpty();
        execution.runInTenant(b, () -> assertThat(context.currentWorkspaceId()).contains(b));
        assertThat(context.currentWorkspaceId()).isEmpty();
    }

    @Test
    void rejectsCrossWorkspaceNestingWithoutChangingOuterBinding_AC_001_7() {
        AtomicInteger calls = new AtomicInteger();
        context.runIn(a, () -> {
            assertThatThrownBy(() -> execution.runInTenant(b, calls::incrementAndGet))
                    .isInstanceOf(IllegalStateException.class);
            assertThat(context.currentWorkspaceId()).contains(a);
        });
        assertThat(calls).hasValue(0);
        assertThat(context.currentWorkspaceId()).isEmpty();
    }

    @Test
    void restoresSameWorkspaceNestedScopeIncludingFailure_AC_001_8() {
        context.runIn(a, () -> {
            execution.runInTenant(
                    a, () -> assertThat(context.currentWorkspaceId()).contains(a));
            assertThatThrownBy(() -> execution.runInTenant(a, () -> {
                        throw new IllegalStateException();
                    }))
                    .isInstanceOf(IllegalStateException.class);
            assertThat(context.currentWorkspaceId()).contains(a);
        });
        assertThat(context.currentWorkspaceId()).isEmpty();
    }

    @Test
    void isolatesConcurrentAndReusedThreads_AC_001_9() throws Exception {
        CountDownLatch entered = new CountDownLatch(2);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var first = pool.submit(() -> execution.runInTenant(a, () -> assertConcurrentScope(a, entered)));
            var second = pool.submit(() -> execution.runInTenant(b, () -> assertConcurrentScope(b, entered)));
            first.get(5, TimeUnit.SECONDS);
            second.get(5, TimeUnit.SECONDS);
            assertThat(pool.submit(context::currentWorkspaceId).get(5, TimeUnit.SECONDS))
                    .isEmpty();
        }
        try (var pool = Executors.newSingleThreadExecutor()) {
            pool.submit(() -> {
                        assertThatThrownBy(() -> execution.runInTenant(a, () -> {
                                    throw new IllegalStateException();
                                }))
                                .isInstanceOf(IllegalStateException.class);
                    })
                    .get(5, TimeUnit.SECONDS);
            pool.submit(() -> execution.runInTenant(
                            b, () -> assertThat(context.currentWorkspaceId()).contains(b)))
                    .get(5, TimeUnit.SECONDS);
            assertThat(pool.submit(context::currentWorkspaceId).get(5, TimeUnit.SECONDS))
                    .isEmpty();
        }
    }

    private void assertConcurrentScope(UUID expected, CountDownLatch entered) {
        entered.countDown();
        try {
            assertThat(entered.await(3, TimeUnit.SECONDS)).isTrue();
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            throw new AssertionError(failure);
        }
        assertThat(context.currentWorkspaceId()).contains(expected);
    }
}
