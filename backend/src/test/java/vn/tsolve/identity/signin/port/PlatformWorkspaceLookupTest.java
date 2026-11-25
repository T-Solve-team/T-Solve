package vn.tsolve.identity.signin.port;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.dao.DataAccessResourceFailureException;
import vn.tsolve.common.tenant.KnownWorkspaces;
import vn.tsolve.common.tenant.TenantContext;
import vn.tsolve.common.tenant.ThreadLocalTenantContext;
import vn.tsolve.identity.signin.SignInConfig;
import vn.tsolve.platform.dto.WorkspaceStatus;
import vn.tsolve.platform.dto.WorkspaceSummary;
import vn.tsolve.platform.service.WorkspaceCatalog;

class PlatformWorkspaceLookupTest {

    private static final String MAIN_TEMPLATE = "https://{slug}.tsolve.test";
    private static final String PORT_TEMPLATE = "http://{slug}.localhost:8080";
    private static final UUID BLUEFIN_ID = UUID.fromString("00000000-0000-4000-8000-0000000000b1");

    private static final WorkspaceSummary BLUEFIN = new WorkspaceSummary(
            BLUEFIN_ID,
            "bluefin",
            WorkspaceStatus.ACTIVE,
            "bluefin.example",
            Optional.of("org-b"),
            Optional.of("https://bluefin.atlassian.net"),
            Optional.of("c-bluefin"));

    private static final WorkspaceSummary NO_ORG = new WorkspaceSummary(
            BLUEFIN_ID,
            "bluefin",
            WorkspaceStatus.ACTIVE,
            "bluefin.example",
            Optional.empty(),
            Optional.of("https://bluefin.atlassian.net"),
            Optional.of("c-bluefin"));

    private static final WorkspaceSummary PAUSED = new WorkspaceSummary(
            BLUEFIN_ID,
            "bluefin",
            WorkspaceStatus.SUSPENDED,
            "bluefin.example",
            Optional.of("org-b"),
            Optional.of("https://bluefin.atlassian.net"),
            Optional.of("c-bluefin"));

    private static ObjectProvider<WorkspaceCatalog> providerWith(WorkspaceCatalog catalog) {
        return new StaticListableBeanFactory(Map.of("workspaceCatalog", catalog))
                .getBeanProvider(WorkspaceCatalog.class);
    }

    private static ObjectProvider<WorkspaceCatalog> emptyProvider() {
        return new StaticListableBeanFactory().getBeanProvider(WorkspaceCatalog.class);
    }

    @Test
    @DisplayName(
            "AC-004.17 a host in any case and with a port finds the Workspace by its site name and maps every field")
    void findsByHostIgnoringCaseAndPort_AC_004_17() {
        WorkspaceCatalog catalog = mock(WorkspaceCatalog.class);
        when(catalog.findBySiteName("bluefin")).thenReturn(Optional.of(BLUEFIN));

        PlatformWorkspaceLookup lookup = new PlatformWorkspaceLookup(providerWith(catalog), MAIN_TEMPLATE);

        assertThat(lookup.findBySiteHost("BlueFin.TSolve.Test:8443"))
                .contains(new WorkspaceRef(
                        BLUEFIN_ID,
                        "bluefin.tsolve.test",
                        "bluefin.example",
                        WorkspaceRef.Status.ACTIVE,
                        "bluefin",
                        "org-b"));
        verify(catalog).findBySiteName("bluefin");

        when(catalog.findBySiteName("bluefin")).thenReturn(Optional.of(NO_ORG));
        assertThat(lookup.findBySiteHost("bluefin.tsolve.test"))
                .contains(new WorkspaceRef(
                        BLUEFIN_ID,
                        "bluefin.tsolve.test",
                        "bluefin.example",
                        WorkspaceRef.Status.ACTIVE,
                        "bluefin",
                        null));

        PlatformWorkspaceLookup portLookup = new PlatformWorkspaceLookup(providerWith(catalog), PORT_TEMPLATE);
        assertThat(portLookup.findBySiteHost("bluefin.localhost:8080"))
                .contains(new WorkspaceRef(
                        BLUEFIN_ID,
                        "bluefin.localhost",
                        "bluefin.example",
                        WorkspaceRef.Status.ACTIVE,
                        "bluefin",
                        null));
    }

    @Test
    @DisplayName(
            "edge 81 an unknown host, a host outside the template or a blank host is empty; a blank host queries nothing")
    void unknownOutsideOrBlankHostIsEmpty_edge81() {
        WorkspaceCatalog catalog = mock(WorkspaceCatalog.class);
        PlatformWorkspaceLookup lookup = new PlatformWorkspaceLookup(providerWith(catalog), MAIN_TEMPLATE);

        assertThat(lookup.findBySiteHost("nosuch.tsolve.test")).isEmpty();
        verify(catalog).findBySiteName("nosuch");

        for (String outside : List.of(
                "bluefin.other.test", "tsolve.test", "bluefin.tsolve.test.evil.test", "a.bluefin.tsolve.test")) {
            assertThat(lookup.findBySiteHost(outside)).as(outside).isEmpty();
        }

        WorkspaceCatalog freshCatalog = mock(WorkspaceCatalog.class);
        PlatformWorkspaceLookup freshLookup = new PlatformWorkspaceLookup(providerWith(freshCatalog), MAIN_TEMPLATE);
        for (String blank : new String[] {null, "", "  "}) {
            assertThat(freshLookup.findBySiteHost(blank)).isEmpty();
        }
        verifyNoInteractions(freshCatalog);
    }

    @Test
    @DisplayName("AC-004.17 a Suspended Workspace comes through as SUSPENDED")
    void suspendedWorkspaceKeepsItsStatus_AC_004_17() {
        WorkspaceCatalog catalog = mock(WorkspaceCatalog.class);
        when(catalog.findBySiteName("bluefin")).thenReturn(Optional.of(PAUSED));
        when(catalog.findById(BLUEFIN_ID)).thenReturn(Optional.of(PAUSED));
        PlatformWorkspaceLookup lookup = new PlatformWorkspaceLookup(providerWith(catalog), MAIN_TEMPLATE);

        assertThat(lookup.findBySiteHost("bluefin.tsolve.test"))
                .map(WorkspaceRef::status)
                .contains(WorkspaceRef.Status.SUSPENDED);
        assertThat(lookup.findById(BLUEFIN_ID)).map(WorkspaceRef::status).contains(WorkspaceRef.Status.SUSPENDED);
    }

    @Test
    @DisplayName(
            "AC-004.17 findById maps every field with the site host built from the template; an unknown id is empty")
    void findsByIdAndBuildsTheSiteHost_AC_004_17() {
        WorkspaceCatalog catalog = mock(WorkspaceCatalog.class);
        when(catalog.findById(BLUEFIN_ID)).thenReturn(Optional.of(BLUEFIN));

        PlatformWorkspaceLookup lookup = new PlatformWorkspaceLookup(providerWith(catalog), MAIN_TEMPLATE);
        assertThat(lookup.findById(BLUEFIN_ID))
                .contains(new WorkspaceRef(
                        BLUEFIN_ID,
                        "bluefin.tsolve.test",
                        "bluefin.example",
                        WorkspaceRef.Status.ACTIVE,
                        "bluefin",
                        "org-b"));

        PlatformWorkspaceLookup portLookup = new PlatformWorkspaceLookup(providerWith(catalog), PORT_TEMPLATE);
        assertThat(portLookup.findById(BLUEFIN_ID)).map(WorkspaceRef::siteHost).contains("bluefin.localhost");

        assertThat(lookup.findById(UUID.randomUUID())).isEmpty();
    }

    @Test
    @DisplayName("edge 83 with no catalog bean both lookups are empty and nothing fails")
    void noCatalogAnswersEmpty_edge83() {
        PlatformWorkspaceLookup lookup = new PlatformWorkspaceLookup(emptyProvider(), MAIN_TEMPLATE);

        assertThat(lookup.findBySiteHost("bluefin.tsolve.test")).isEmpty();
        assertThat(lookup.findById(BLUEFIN_ID)).isEmpty();
    }

    @Test
    @DisplayName("edge 80 a Registry failure propagates from both lookups, never as empty")
    void registryFailurePropagates_edge80() {
        WorkspaceCatalog catalog = mock(WorkspaceCatalog.class);
        DataAccessResourceFailureException failure = new DataAccessResourceFailureException("registry down");
        when(catalog.findBySiteName("bluefin")).thenThrow(failure);
        when(catalog.findById(BLUEFIN_ID)).thenThrow(failure);

        PlatformWorkspaceLookup lookup = new PlatformWorkspaceLookup(providerWith(catalog), MAIN_TEMPLATE);

        assertThatThrownBy(() -> lookup.findBySiteHost("bluefin.tsolve.test")).isSameAs(failure);
        assertThatThrownBy(() -> lookup.findById(BLUEFIN_ID)).isSameAs(failure);
    }

    @Test
    @DisplayName(
            "edge 86 with a catalog and a blank TSOLVE_SITE_ORIGIN_TEMPLATE the startup fails naming the variable; with a template it starts")
    void blankTemplateWithACatalogFailsStartup_edge86() {
        ApplicationContextRunner runner = new ApplicationContextRunner()
                .withUserConfiguration(
                        PlatformWorkspaceLookup.class, ThreadLocalTenantContext.class, CyclicCatalogConfig.class);

        for (String badTemplate : List.of("", "  ", "https://tsolve.test")) {
            runner.withPropertyValues("TSOLVE_SITE_ORIGIN_TEMPLATE=" + badTemplate)
                    .run(context -> {
                        assertThat(context).hasFailed();
                        assertThat(context.getStartupFailure()).hasStackTraceContaining("TSOLVE_SITE_ORIGIN_TEMPLATE");
                    });
        }

        runner.withPropertyValues("TSOLVE_SITE_ORIGIN_TEMPLATE=" + MAIN_TEMPLATE)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean(WorkspaceLookup.class).findBySiteHost("bluefin.tsolve.test"))
                            .map(WorkspaceRef::id)
                            .contains(BLUEFIN_ID);
                });
    }

    /** Not annotated: a component scan in another test must not pick this class up; the runner registers it. */
    static class CyclicCatalogConfig {
        @Bean
        KnownWorkspaces knownWorkspaces(WorkspaceLookup lookup) {
            return new SignInConfig().knownWorkspaces(lookup);
        }

        @Bean
        WorkspaceCatalog workspaceCatalog(TenantContext tenantContext) {
            return new CyclicCatalog(tenantContext);
        }
    }

    private static final class CyclicCatalog implements WorkspaceCatalog {
        @SuppressWarnings("unused")
        private final TenantContext tenantContext;

        private CyclicCatalog(TenantContext tenantContext) {
            this.tenantContext = tenantContext;
        }

        @Override
        public Optional<WorkspaceSummary> findById(UUID workspaceId) {
            return BLUEFIN_ID.equals(workspaceId) ? Optional.of(BLUEFIN) : Optional.empty();
        }

        @Override
        public Optional<WorkspaceSummary> findBySiteName(String siteName) {
            return "bluefin".equalsIgnoreCase(siteName) ? Optional.of(BLUEFIN) : Optional.empty();
        }

        @Override
        public Optional<WorkspaceSummary> findByCloudId(String cloudId) {
            return Optional.empty();
        }

        @Override
        public List<WorkspaceSummary> findActive() {
            return List.of(BLUEFIN);
        }

        @Override
        public WorkspaceSummary linkJiraSite(String siteUrl) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void claimEmail(String email) {
            throw new UnsupportedOperationException();
        }
    }
}
