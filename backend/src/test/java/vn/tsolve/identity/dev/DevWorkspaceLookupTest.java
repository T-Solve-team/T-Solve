package vn.tsolve.identity.dev;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import vn.tsolve.identity.dev.DevAccounts.DevAccount;
import vn.tsolve.identity.signin.port.NoWorkspaceLookup;
import vn.tsolve.identity.signin.port.WorkspaceLookup;
import vn.tsolve.identity.signin.port.WorkspaceRef;

/** Task 23a: the dev Workspaces lookup and the dev accounts. No Docker. */
class DevWorkspaceLookupTest {

    private final DevWorkspaceLookup lookup = new DevWorkspaceLookup();

    @Test
    @DisplayName("edge 125 resolves acme.localhost to the acme dev Workspace")
    void resolvesAcmeToItsDevWorkspace_edge125() {
        Optional<WorkspaceRef> found = lookup.findBySiteHost("acme.localhost");

        assertThat(found).isPresent();
        WorkspaceRef acme = found.get();
        assertThat(acme.id()).isEqualTo(DevAccounts.ACME_ID);
        assertThat(acme.slug()).isEqualTo("acme").isEqualTo(DevAccounts.ACME_SLUG);
        assertThat(acme.siteHost()).isEqualTo("acme.localhost");
        assertThat(acme.googleDomain()).isEqualTo("acme.test");
        assertThat(acme.organizationId()).isNotBlank().isEqualTo(DevAccounts.ACME_ORGANIZATION_ID);
        assertThat(acme.status()).isEqualTo(WorkspaceRef.Status.ACTIVE);
        assertThat(lookup.findById(DevAccounts.ACME_ID)).contains(acme);
        assertThat(new DevWorkspaceLookup().findBySiteHost("acme.localhost")).contains(acme);
    }

    @Test
    @DisplayName("edge 125 resolves beta.localhost to the beta dev Workspace, apart from acme")
    void resolvesBetaApartFromAcme_edge125() {
        Optional<WorkspaceRef> found = lookup.findBySiteHost("beta.localhost");

        assertThat(found).isPresent();
        WorkspaceRef beta = found.get();
        assertThat(beta.id()).isEqualTo(DevAccounts.BETA_ID);
        assertThat(beta.slug()).isEqualTo("beta").isEqualTo(DevAccounts.BETA_SLUG);
        assertThat(beta.siteHost()).isEqualTo("beta.localhost");
        assertThat(beta.googleDomain()).isEqualTo("beta.test");
        assertThat(beta.organizationId()).isNotBlank().isEqualTo(DevAccounts.BETA_ORGANIZATION_ID);
        assertThat(beta.status()).isEqualTo(WorkspaceRef.Status.ACTIVE);
        assertThat(beta.id()).isNotEqualTo(DevAccounts.ACME_ID);
        assertThat(beta.organizationId()).isNotEqualTo(DevAccounts.ACME_ORGANIZATION_ID);
        assertThat(lookup.findById(DevAccounts.BETA_ID)).contains(beta);
    }

    @ParameterizedTest(name = "[{index}] host=[{0}]")
    @EmptySource
    @ValueSource(
            strings = {
                "localhost",
                "acme",
                "login.localhost",
                "admin.localhost",
                "api.localhost",
                "gamma.localhost",
                "acme.tsolve.io.vn",
                "acme.localhost.evil.example",
                "xacme.localhost",
                "acme-localhost"
            })
    @DisplayName("edge 126 answers nothing for a host that is no dev Workspace")
    void answersNothingForUnknownHost_edge126(String host) {
        assertThat(lookup.findBySiteHost(host)).isEmpty();
    }

    @Test
    @DisplayName("edge 126 answers nothing for an id that is no dev Workspace")
    void answersNothingForUnknownId_edge126() {
        assertThat(lookup.findById(UUID.randomUUID())).isEmpty();
    }

    @Test
    @DisplayName("edge 127 every dev account belongs to a dev Workspace of its email's domain")
    void devAccountsAgreeWithDevWorkspaces_edge127() {
        List<DevAccount> accounts = DevAccounts.all();

        assertThat(accounts).isNotEmpty();
        assertThat(accounts).extracting(DevAccount::workspaceId).contains(DevAccounts.ACME_ID, DevAccounts.BETA_ID);
        assertThat(accounts).extracting(DevAccount::email).doesNotHaveDuplicates();
        for (DevAccount account : accounts) {
            Optional<WorkspaceRef> workspace = lookup.findById(account.workspaceId());
            assertThat(workspace).as("workspace of %s", account.email()).isPresent();
            String domain =
                    account.email().substring(account.email().indexOf('@') + 1).toLowerCase();
            assertThat(domain)
                    .as("domain of %s", account.email())
                    .isEqualTo(workspace.get().googleDomain());
        }
    }

    @Test
    @DisplayName("edge 128 in the dev profile the WorkspaceLookup is DevWorkspaceLookup")
    void devProfileUsesDevWorkspaceLookup_edge128() {
        new ApplicationContextRunner()
                .withUserConfiguration(NoWorkspaceLookup.class, DevWorkspaceLookup.class)
                .withPropertyValues("spring.profiles.active=dev")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(WorkspaceLookup.class);
                    assertThat(context.getBean(WorkspaceLookup.class)).isInstanceOf(DevWorkspaceLookup.class);
                    assertThat(context.getBean(WorkspaceLookup.class).findBySiteHost("acme.localhost"))
                            .isPresent();
                });
    }

    @ParameterizedTest(name = "[{index}] profile=[{0}]")
    @ValueSource(strings = {"", "test"})
    @DisplayName(
            "edge 128 outside the dev profile there is no DevWorkspaceLookup and acme.localhost resolves to nothing")
    void noDevWorkspaceLookupOutsideDevProfile_edge128(String profile) {
        new ApplicationContextRunner()
                .withUserConfiguration(NoWorkspaceLookup.class, DevWorkspaceLookup.class)
                .withPropertyValues("spring.profiles.active=" + profile)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean(DevWorkspaceLookup.class);
                    assertThat(context.getBean(WorkspaceLookup.class)).isInstanceOf(NoWorkspaceLookup.class);
                    assertThat(context.getBean(WorkspaceLookup.class).findBySiteHost("acme.localhost"))
                            .isEmpty();
                });
    }
}
