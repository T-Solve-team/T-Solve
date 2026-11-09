package vn.tsolve.identity.keycloak;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import vn.tsolve.identity.signin.port.NoopWorkspaceSignInProvisioner;
import vn.tsolve.identity.signin.port.WorkspaceSignInProvisioner.ProvisionedObjects;

class NoopWorkspaceSignInProvisionerTest {

    @Test
    @DisplayName(
            "AC-001.60 the no-op provisioner creates nothing, returns no Organization id, reports no object and removes nothing")
    void noopCreatesNothing_AC_001_60() throws Exception {
        // a no-argument constructor: it can reach no Keycloak or HTTP client
        NoopWorkspaceSignInProvisioner noop = NoopWorkspaceSignInProvisioner.class
                .getDeclaredConstructor()
                .newInstance();
        UUID workspaceId = UUID.randomUUID();

        assertThat(noop.provision(workspaceId, "gamma", "gamma.test")).isEmpty();
        assertThat(noop.check(workspaceId)).isEqualTo(new ProvisionedObjects(false, false, false, false));
        assertThatCode(() -> noop.remove(workspaceId)).doesNotThrowAnyException();
        assertThat(noop.check(workspaceId)).isEqualTo(new ProvisionedObjects(false, false, false, false));
        assertThat(NoopWorkspaceSignInProvisioner.class.getDeclaredConstructors()).hasSize(1);
        assertThat(NoopWorkspaceSignInProvisioner.class.getDeclaredFields()).isEmpty();
    }
}
