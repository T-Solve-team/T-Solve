package vn.tsolve.common.tenant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.tsolve.common.exception.GlobalExceptionHandler;
import vn.tsolve.identity.signin.port.InMemoryWorkspaceLookup;
import vn.tsolve.platform.service.impl.StubTenantContext;

/**
 * The one per-thread Workspace binding (Task 6a-1). {@code KnownWorkspaces} here knows A and B only; every test ends
 * with an empty holder, because the {@code ThreadLocal} is static and a leaked binding would reach the next test.
 */
@ExtendWith(OutputCaptureExtension.class)
class ThreadLocalTenantContextTest {

    private static final UUID A = InMemoryWorkspaceLookup.ACME.id();
    private static final UUID B = InMemoryWorkspaceLookup.BETA.id();
    private static final UUID UNKNOWN = UUID.fromString("00000000-0000-4000-8000-0000000000ff");

    private final Set<UUID> known = Set.of(A, B);
    private final KnownWorkspaces knownWorkspaces = known::contains;
    private final ThreadLocalTenantContext holder = new ThreadLocalTenantContext(knownWorkspaces);

    @AfterEach
    void nothingIsLeftBound() {
        assertThat(holder.currentWorkspaceId()).as("binding left on the thread").isEmpty();
    }

    @Test
    @DisplayName(
            "AC-002.12 runs the action in the Workspace when none or the same one is bound, and restores the binding after")
    void runsInTheWorkspaceAndRestoresTheBinding() {
        AtomicReference<Optional<UUID>> seen = new AtomicReference<>();
        holder.runIn(A, () -> seen.set(holder.currentWorkspaceId()));
        assertThat(seen.get()).contains(A);
        assertThat(holder.currentWorkspaceId()).isEmpty();

        String result = holder.runIn(A, () -> holder.requireWorkspaceId() + "!");
        assertThat(result).isEqualTo(A + "!");
        assertThat(holder.currentWorkspaceId()).isEmpty();

        AtomicReference<Optional<UUID>> nestedSeen = new AtomicReference<>();
        AtomicReference<Optional<UUID>> afterNested = new AtomicReference<>();
        holder.runIn(A, () -> {
            holder.runIn(A, () -> nestedSeen.set(holder.currentWorkspaceId()));
            afterNested.set(holder.currentWorkspaceId());
        });
        assertThat(nestedSeen.get()).contains(A);
        assertThat(afterNested.get())
                .as("the outer binding is not removed by the nested run")
                .contains(A);

        Optional<UUID> afterNestedSupplier = holder.runIn(A, () -> {
            assertThat(holder.runIn(A, () -> holder.requireWorkspaceId())).isEqualTo(A);
            return holder.currentWorkspaceId();
        });
        assertThat(afterNestedSupplier).contains(A);
    }

    @Test
    @DisplayName("AC-002.12 edge 2 restores the binding when the action throws and rethrows the same exception")
    void restoresTheBindingWhenTheActionThrows() {
        IllegalArgumentException failure = new IllegalArgumentException("boom");
        assertThatThrownBy(() -> holder.runIn(A, () -> {
                    throw failure;
                }))
                .isSameAs(failure);
        assertThat(holder.currentWorkspaceId()).isEmpty();
        AtomicInteger ran = new AtomicInteger();
        holder.runIn(B, ran::incrementAndGet);
        assertThat(ran)
                .as("a following runIn of another Workspace runs, so nothing leaked")
                .hasValue(1);

        IllegalStateException runnableFailure = new IllegalStateException("boom2");
        assertThatThrownBy(() -> holder.runIn(A, (Runnable) () -> {
                    throw runnableFailure;
                }))
                .isSameAs(runnableFailure);
        assertThat(holder.currentWorkspaceId()).isEmpty();

        AtomicReference<Optional<UUID>> outerAfter = new AtomicReference<>();
        holder.runIn(A, () -> {
            assertThatThrownBy(() -> holder.runIn(A, () -> {
                        throw failure;
                    }))
                    .isSameAs(failure);
            outerAfter.set(holder.currentWorkspaceId());
        });
        assertThat(outerAfter.get())
                .as("the outer binding survives a failing nested run")
                .contains(A);
    }

    @Test
    @DisplayName(
            "AC-002.13 edge 4 refuses another Workspace while one is bound, does not run the action and keeps the bound one")
    void refusesAnotherWorkspaceWhileOneIsBound() {
        AtomicInteger ran = new AtomicInteger();
        AtomicReference<Optional<UUID>> afterRefusal = new AtomicReference<>();
        holder.runIn(A, () -> {
            assertThatThrownBy(() -> holder.runIn(B, (Runnable) ran::incrementAndGet))
                    .isInstanceOf(RuntimeException.class);
            assertThatThrownBy(() -> holder.runIn(B, () -> ran.incrementAndGet()))
                    .isInstanceOf(RuntimeException.class);
            afterRefusal.set(holder.currentWorkspaceId());
        });
        assertThat(ran).as("the refused action never ran").hasValue(0);
        assertThat(afterRefusal.get()).contains(A);
    }

    @Test
    @DisplayName(
            "AC-002.14 refuses an id that KnownWorkspaces does not know, and a null id, without running the action")
    void refusesAnUnknownOrNullWorkspace() {
        AtomicInteger ran = new AtomicInteger();

        assertThatThrownBy(() -> holder.runIn(UNKNOWN, (Runnable) ran::incrementAndGet))
                .isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> holder.runIn(UNKNOWN, () -> ran.incrementAndGet()))
                .isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> holder.runIn(null, (Runnable) ran::incrementAndGet))
                .isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> holder.runIn(null, () -> ran.incrementAndGet()))
                .isInstanceOf(RuntimeException.class);

        assertThat(ran).hasValue(0);
        assertThat(holder.currentWorkspaceId()).isEmpty();
    }

    @Test
    @DisplayName(
            "AC-002.16 requireWorkspaceId with none bound throws NoTenantException and logs the calling class and method")
    void requireWithNoneBoundThrowsAndLogsTheCaller(CapturedOutput output) {
        assertThatThrownBy(this::theMethodThatReadsTheWorkspaceWithNoneBound).isInstanceOf(NoTenantException.class);

        assertThat(output.getAll())
                .contains("ThreadLocalTenantContextTest")
                .contains("theMethodThatReadsTheWorkspaceWithNoneBound")
                .doesNotContainPattern("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
    }

    private UUID theMethodThatReadsTheWorkspaceWithNoneBound() {
        return holder.requireWorkspaceId();
    }

    @RestController
    static class WorkspaceReadingController {
        private final TenantContext context;

        WorkspaceReadingController(TenantContext context) {
            this.context = context;
        }

        @GetMapping("/test/workspace")
        String read() {
            return context.requireWorkspaceId().toString();
        }
    }

    @Test
    @DisplayName("AC-002.16 the global handler answers NoTenantException with 500 and code MSG06")
    void theGlobalHandlerAnswers500Msg06() throws Exception {
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new WorkspaceReadingController(holder))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        mvc.perform(get("/test/workspace"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("MSG06"));
    }

    @Test
    @DisplayName("AC-002.16 currentWorkspaceId with none bound is empty and does not throw")
    void currentWorkspaceIdWithNoneBoundIsEmpty() {
        assertThat(holder.currentWorkspaceId()).isEmpty();
    }

    @Test
    @DisplayName("edge 2 a thread started inside runIn starts with no Workspace bound")
    void aThreadStartedInsideRunInStartsUnbound() throws InterruptedException {
        AtomicReference<Optional<UUID>> seenByThread = new AtomicReference<>();
        holder.runIn(A, () -> {
            Thread other = new Thread(() -> seenByThread.set(holder.currentWorkspaceId()));
            other.start();
            try {
                other.join(5000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        assertThat(seenByThread.get()).isNotNull().isEmpty();
    }

    @Test
    @DisplayName("edge 4 a second holder instance sees the binding of the first and refuses another Workspace")
    void theBindingIsSharedBetweenHolderInstances() {
        ThreadLocalTenantContext second = new ThreadLocalTenantContext(knownWorkspaces);
        AtomicInteger ran = new AtomicInteger();
        AtomicReference<Optional<UUID>> seenBySecond = new AtomicReference<>();

        holder.runIn(A, () -> {
            seenBySecond.set(second.currentWorkspaceId());
            assertThatThrownBy(() -> second.runIn(B, (Runnable) ran::incrementAndGet))
                    .isInstanceOf(RuntimeException.class);
        });

        assertThat(seenBySecond.get()).contains(A);
        assertThat(ran).hasValue(0);
    }

    @Test
    @DisplayName(
            "AC-002.13 the injected TenantContext is the holder, not the stub, while TSOLVE_STUB_WORKSPACE_ID is set")
    void theInjectedTenantContextIsTheHolderNotTheStub() {
        new ApplicationContextRunner()
                .withUserConfiguration(ThreadLocalTenantContext.class, StubTenantContext.class)
                .withBean(KnownWorkspaces.class, () -> knownWorkspaces)
                .withPropertyValues("tsolve.stub.workspace-id=" + A)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean(TenantContext.class)).isInstanceOf(ThreadLocalTenantContext.class);
                    assertThat(context.getBeanProvider(TenantContext.class).getIfAvailable())
                            .isInstanceOf(ThreadLocalTenantContext.class);

                    TenantContext injected = context.getBean(TenantContext.class);
                    AtomicInteger ran = new AtomicInteger();
                    injected.runIn(A, () -> assertThatThrownBy(() -> injected.runIn(B, (Runnable) ran::incrementAndGet))
                            .isInstanceOf(RuntimeException.class));
                    assertThat(ran).as("the stub would simply have run it").hasValue(0);
                });
    }
}
