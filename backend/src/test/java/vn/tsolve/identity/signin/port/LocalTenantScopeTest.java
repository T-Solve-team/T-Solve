package vn.tsolve.identity.signin.port;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class LocalTenantScopeTest {

    private static final UUID ACME = InMemoryWorkspaceLookup.ACME.id();
    private static final UUID BETA = InMemoryWorkspaceLookup.BETA.id();

    private final LocalTenantScope scope = new LocalTenantScope();

    /** Any runtime exception counts, but "not implemented" is not an answer. */
    private void assertNoScopeOpen() {
        assertThatThrownBy(scope::currentWorkspaceId)
                .isInstanceOf(RuntimeException.class)
                .isNotInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("edge 93 currentWorkspaceId throws when no scope is open")
    void currentWorkspaceIdThrowsWhenNoScopeIsOpen_edge93() {
        assertNoScopeOpen();
    }

    @Test
    @DisplayName("AC-001.15 runIn binds the Workspace for the supplier and returns its result")
    void runInBindsTheWorkspaceAndReturnsTheResult_AC_001_15() {
        String result = scope.runIn(ACME, () -> scope.currentWorkspaceId() + "!");

        assertThat(result).isEqualTo(ACME + "!");
    }

    @Test
    @DisplayName("edge 93 runIn clears the binding after the supplier returns or throws")
    void runInClearsTheBinding_edge93() {
        scope.runIn(ACME, () -> "done");
        assertNoScopeOpen();

        IllegalArgumentException failure = new IllegalArgumentException("boom");
        assertThatThrownBy(() -> scope.runIn(ACME, () -> {
                    throw failure;
                }))
                .isSameAs(failure);
        assertNoScopeOpen();
    }

    @Test
    @DisplayName("edge 93 a binding is not seen by another thread")
    void aBindingIsNotSeenByAnotherThread_edge93() {
        AtomicReference<Throwable> seenByOther = new AtomicReference<>();

        scope.runIn(ACME, () -> {
            Thread other = new Thread(() -> {
                try {
                    scope.currentWorkspaceId();
                } catch (RuntimeException e) {
                    if (!(e instanceof UnsupportedOperationException)) {
                        seenByOther.set(e);
                    }
                }
            });
            other.start();
            try {
                other.join(5000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return null;
        });

        assertThat(seenByOther.get())
                .as("the other thread's currentWorkspaceId threw")
                .isNotNull();
    }

    @Test
    @DisplayName("edge 93 a nested runIn with another Workspace throws and never runs the inner supplier")
    void aNestedRunInWithAnotherWorkspaceThrows_edge93() {
        AtomicBoolean innerRan = new AtomicBoolean();

        assertThatThrownBy(() -> scope.runIn(
                        ACME,
                        () -> scope.runIn(BETA, () -> {
                            innerRan.set(true);
                            return "inner";
                        })))
                .isInstanceOf(IllegalStateException.class);

        assertThat(innerRan).isFalse();
        assertNoScopeOpen();
    }
}
