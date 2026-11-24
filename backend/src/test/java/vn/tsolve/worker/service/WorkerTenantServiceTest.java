package vn.tsolve.worker.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class WorkerTenantServiceTest {

    @Test
    @DisplayName("AC-002.12 enqueueWorkspaceId has no default, so every implementation must decide")
    void enqueueWorkspaceIdIsAbstract_AC_002_12() throws NoSuchMethodException {
        var method = WorkerTenantService.class.getMethod("enqueueWorkspaceId");

        assertThat(method.isDefault())
                .as("a forgotten override must not compile")
                .isFalse();
    }
}
