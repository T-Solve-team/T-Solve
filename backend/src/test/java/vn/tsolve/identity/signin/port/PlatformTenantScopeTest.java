package vn.tsolve.identity.signin.port;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import vn.tsolve.common.tenant.KnownWorkspaces;
import vn.tsolve.common.tenant.NoTenantException;
import vn.tsolve.common.tenant.TenantContext;
import vn.tsolve.common.tenant.ThreadLocalTenantContext;
import vn.tsolve.identity.signin.SignInConfig;

/** The identity {@link TenantScope} is the holder under another name (Task 6a-1): one binding, one Workspace list. */
@ExtendWith(OutputCaptureExtension.class)
class PlatformTenantScopeTest {

    private static final UUID A = InMemoryWorkspaceLookup.ACME.id();
    private static final UUID B = InMemoryWorkspaceLookup.BETA.id();
    private static final UUID SUSPENDED = InMemoryWorkspaceLookup.GAMMA.id();
    private static final UUID UNKNOWN = UUID.fromString("00000000-0000-4000-8000-0000000000ff");

    private final SignInConfig config = new SignInConfig();
    private final KnownWorkspaces known = config.knownWorkspaces(new InMemoryWorkspaceLookup());
    private final TenantContext holder = new ThreadLocalTenantContext(known);
    private final TenantScope scope = config.tenantScope(holder);

    @AfterEach
    void nothingIsLeftBound() {
        assertThat(holder.currentWorkspaceId()).as("binding left on the thread").isEmpty();
    }

    @Test
    @DisplayName("AC-002.12 TenantScope and TenantContext read and write the same binding")
    void theScopeAndTheHolderShareOneBinding() {
        assertThat(scope).isInstanceOf(PlatformTenantScope.class);

        AtomicReference<Optional<UUID>> holderSees = new AtomicReference<>();
        scope.runIn(A, () -> {
            holderSees.set(holder.currentWorkspaceId());
            return null;
        });
        assertThat(holderSees.get()).contains(A);
        assertThat(holder.currentWorkspaceId()).isEmpty();

        AtomicReference<UUID> scopeSees = new AtomicReference<>();
        holder.runIn(B, () -> scopeSees.set(scope.currentWorkspaceId()));
        assertThat(scopeSees.get()).isEqualTo(B);
        assertThat(holder.currentWorkspaceId()).isEmpty();
    }

    @Test
    @DisplayName(
            "AC-002.13 edge 4 runIn of another Workspace through TenantScope inside a TenantContext binding throws and does not run")
    void anotherWorkspaceThroughTheScopeInsideAHolderBindingIsRefused() {
        AtomicInteger ran = new AtomicInteger();
        AtomicReference<Optional<UUID>> afterRefusal = new AtomicReference<>();

        holder.runIn(A, () -> {
            assertThatThrownBy(() -> scope.runIn(B, () -> ran.incrementAndGet()))
                    .isInstanceOf(RuntimeException.class);
            afterRefusal.set(holder.currentWorkspaceId());
        });

        assertThat(ran).hasValue(0);
        assertThat(afterRefusal.get()).contains(A);
    }

    @Test
    @DisplayName(
            "AC-002.14 refuses through TenantScope an id that WorkspaceLookup does not know, without running the action")
    void anUnknownWorkspaceThroughTheScopeIsRefused() {
        AtomicInteger ran = new AtomicInteger();

        assertThatThrownBy(() -> scope.runIn(UNKNOWN, () -> ran.incrementAndGet()))
                .isInstanceOf(RuntimeException.class);

        assertThat(ran).hasValue(0);
    }

    @Test
    @DisplayName("AC-002.15 lets a Suspended Workspace bind through TenantScope")
    void aSuspendedWorkspaceBindsThroughTheScope() {
        AtomicReference<Optional<UUID>> holderSees = new AtomicReference<>();

        String result = scope.runIn(SUSPENDED, () -> {
            holderSees.set(holder.currentWorkspaceId());
            return "ran";
        });

        assertThat(result).isEqualTo("ran");
        assertThat(holderSees.get()).contains(SUSPENDED);
    }

    @Test
    @DisplayName("AC-002.16 currentWorkspaceId with none bound throws NoTenantException")
    void currentWorkspaceIdWithNoneBoundThrowsNoTenantException(CapturedOutput output) {
        assertThatThrownBy(this::theServiceMethodThatAsksTheScope).isInstanceOf(NoTenantException.class);

        // Q7 (a): the logged call site is the first frame outside common.tenant and PlatformTenantScope.
        assertThat(output.getAll()).contains("PlatformTenantScopeTest").contains("theServiceMethodThatAsksTheScope");
    }

    private UUID theServiceMethodThatAsksTheScope() {
        return scope.currentWorkspaceId();
    }
}
