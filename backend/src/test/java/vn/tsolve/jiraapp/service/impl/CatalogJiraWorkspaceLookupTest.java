package vn.tsolve.jiraapp.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import vn.tsolve.platform.dto.WorkspaceStatus;
import vn.tsolve.platform.dto.WorkspaceSummary;
import vn.tsolve.platform.service.WorkspaceCatalog;

class CatalogJiraWorkspaceLookupTest {

    private static final UUID WORKSPACE_A = UUID.fromString("00000000-0000-4000-8000-0000000000a1");
    private static final UUID WORKSPACE_B = UUID.fromString("00000000-0000-4000-8000-0000000000b2");

    private static WorkspaceSummary summary(UUID id, WorkspaceStatus status, String cloudId) {
        return new WorkspaceSummary(
                id, "site", status, "site.example", Optional.empty(), Optional.empty(), Optional.of(cloudId));
    }

    private static ObjectProvider<WorkspaceCatalog> providerWith(WorkspaceCatalog catalog) {
        return new StaticListableBeanFactory(Map.of("workspaceCatalog", catalog))
                .getBeanProvider(WorkspaceCatalog.class);
    }

    private static ObjectProvider<WorkspaceCatalog> emptyProvider() {
        return new StaticListableBeanFactory().getBeanProvider(WorkspaceCatalog.class);
    }

    @Test
    @DisplayName("AC-001.2 maps a known cloudId to its Workspace id, including Suspended Workspaces")
    void mapsAKnownCloudIdToItsWorkspace_AC_001_2() {
        WorkspaceCatalog catalog = mock(WorkspaceCatalog.class);
        when(catalog.findByCloudId("c-a")).thenReturn(Optional.of(summary(WORKSPACE_A, WorkspaceStatus.ACTIVE, "c-a")));
        when(catalog.findByCloudId("c-b"))
                .thenReturn(Optional.of(summary(WORKSPACE_B, WorkspaceStatus.SUSPENDED, "c-b")));

        CatalogJiraWorkspaceLookup lookup = new CatalogJiraWorkspaceLookup(providerWith(catalog));

        assertThat(lookup.workspaceOf("c-a")).contains(WORKSPACE_A);
        assertThat(lookup.workspaceOf("c-b")).contains(WORKSPACE_B);
    }

    @Test
    @DisplayName("AC-001.3 has no Workspace for an unknown or null cloudId")
    void hasNoWorkspaceForAnUnknownOrNullCloudId_AC_001_3() {
        WorkspaceCatalog catalog = mock(WorkspaceCatalog.class);
        when(catalog.findByCloudId("c-x")).thenReturn(Optional.empty());

        CatalogJiraWorkspaceLookup lookup = new CatalogJiraWorkspaceLookup(providerWith(catalog));

        assertThat(lookup.workspaceOf("c-x")).isEmpty();
        assertThat(lookup.workspaceOf(null)).isEmpty();
        verify(catalog, never()).findByCloudId(null);
    }

    @Test
    @DisplayName("AC-001.3 edge 83 has no Workspace when no catalog bean exists")
    void hasNoWorkspaceWithoutACatalog_AC_001_3_edge83() {
        CatalogJiraWorkspaceLookup lookup = new CatalogJiraWorkspaceLookup(emptyProvider());

        assertThat(lookup.workspaceOf("c-a")).isEmpty();
    }
}
