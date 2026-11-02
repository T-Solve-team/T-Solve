package vn.tsolve.common.tenant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionOperations;

class ScopedTenantContextTest {
    private final ScopedTenantContext context =
            new ScopedTenantContext(id -> id == 1 || id == 2, TransactionOperations.withoutTransaction());

    @Test
    void
            restoresScopesAndRejectsWorkspaceSwitch_AC_002_1_AC_002_11_AC_002_12_AC_002_13_AC_002_14_AC_002_15_AC_002_16() {
        assertThat(context.currentWorkspaceId()).isEmpty();
        assertThatThrownBy(context::requireWorkspaceId).isInstanceOf(TenantAccessException.class);
        assertThatCode(() -> context.runIn(1L, () -> "ok")).doesNotThrowAnyException();
        assertThat(context.runIn(1L, () -> {
                    assertThat(context.requireWorkspaceId()).isEqualTo(1L);
                    assertThat(context.runIn(1L, context::requireWorkspaceId)).isEqualTo(1L);
                    assertThatThrownBy(() -> context.runIn(2L, () -> "must not run"))
                            .isInstanceOf(TenantAccessException.class);
                    return "ok";
                }))
                .isEqualTo("ok");
        assertThat(context.currentWorkspaceId()).isEmpty();
        assertThatThrownBy(() -> context.runIn(999L, () -> "must not run")).isInstanceOf(TenantAccessException.class);
        assertThatThrownBy(() -> context.runIn(1L, () -> {
                    throw new IllegalArgumentException("test failure");
                }))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(context.currentWorkspaceId()).isEmpty();
        // The internal binding accepts any existing Workspace, including Suspended; web policy is separate.
        assertThat(context.runIn(2L, context::requireWorkspaceId)).isEqualTo(2L);
    }
}
