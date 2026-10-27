package vn.tsolve.common.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class WorkspaceContextTest {

    @Test
    void current_isNoneWhenNothingIsSet() {
        assertThat(WorkspaceContext.current()).isEqualTo(WorkspaceContext.NONE);
    }

    @Test
    void callAs_setsWorkspaceOnlyForTheAction_AC_003_7() {
        long inside = WorkspaceContext.callAs(7L, WorkspaceContext::current);

        assertThat(inside).isEqualTo(7L);
        assertThat(WorkspaceContext.current()).isEqualTo(WorkspaceContext.NONE);
    }

    @Test
    void callAs_restoresTheOuterWorkspace_AC_004_9() {
        // The worker runs as ROOT and switches to each event's Workspace while it processes the event.
        long[] seen = new long[3];

        WorkspaceContext.runAs(WorkspaceContext.ROOT, () -> {
            seen[0] = WorkspaceContext.current();
            WorkspaceContext.runAs(7L, () -> seen[1] = WorkspaceContext.current());
            seen[2] = WorkspaceContext.current();
        });

        assertThat(seen).containsExactly(WorkspaceContext.ROOT, 7L, WorkspaceContext.ROOT);
        assertThat(WorkspaceContext.current()).isEqualTo(WorkspaceContext.NONE);
    }

    @Test
    void callAs_restoresWorkspaceWhenTheActionThrows() {
        assertThatThrownBy(() -> WorkspaceContext.runAs(7L, () -> {
                    throw new IllegalStateException("boom");
                }))
                .isInstanceOf(IllegalStateException.class);

        assertThat(WorkspaceContext.current()).isEqualTo(WorkspaceContext.NONE);
    }
}
