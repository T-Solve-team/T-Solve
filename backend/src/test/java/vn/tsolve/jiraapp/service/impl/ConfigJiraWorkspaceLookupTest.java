package vn.tsolve.jiraapp.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class ConfigJiraWorkspaceLookupTest {

    private static final UUID ACME = UUID.fromString("00000000-0000-0000-0000-00000000000a");
    private static final UUID BETA = UUID.fromString("00000000-0000-0000-0000-00000000000b");

    @Test
    void mapsEachRegisteredSiteToItsWorkspace_AC_001_2() {
        ConfigJiraWorkspaceLookup lookup =
                new ConfigJiraWorkspaceLookup(" cloud-a = " + ACME + " ;cloud-b=" + BETA + ";");

        assertThat(lookup.workspaceOf("cloud-a")).contains(ACME);
        assertThat(lookup.workspaceOf("cloud-b")).contains(BETA);
    }

    @Test
    void hasNoWorkspaceForAnUnregisteredSite_AC_001_3() {
        ConfigJiraWorkspaceLookup lookup = new ConfigJiraWorkspaceLookup("cloud-a=" + ACME);

        assertThat(lookup.workspaceOf("cloud-x")).isEmpty();
        assertThat(lookup.workspaceOf(null)).isEmpty();
        assertThat(new ConfigJiraWorkspaceLookup("").workspaceOf("cloud-a")).isEmpty();
    }

    @Test
    void refusesToStartWithAMalformedEntry() {
        assertThatThrownBy(() -> new ConfigJiraWorkspaceLookup("cloud-a"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("TSOLVE_STUB_JIRA_SITES");
        assertThatThrownBy(() -> new ConfigJiraWorkspaceLookup("cloud-a=not-a-uuid"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("entry 1");
    }
}
