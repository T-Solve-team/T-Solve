package vn.tsolve.ticket.service.impl;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;

/** The Jira key of a Project, read by the stub until project-integration links real Projects (design: Jira key). */
class JiraProjectKeyStubTest {

    private static final UUID ONE = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID TWO = UUID.fromString("00000000-0000-0000-0000-000000000002");

    @Test
    void readsTheJiraKeyOfEachListedProject() {
        StubProjectAccess stub = new StubProjectAccess(
                "", "", "00000000-0000-0000-0000-000000000001:KAN, 00000000-0000-0000-0000-000000000002 : OPS");

        assertThat(stub.jiraProjectKey(ONE)).contains("KAN");
        assertThat(stub.jiraProjectKey(TWO)).contains("OPS");
    }

    @Test
    void aProjectWithoutAKeyOrAMalformedEntryHasNone() {
        StubProjectAccess stub = new StubProjectAccess("", "", "1-1-1-1-1:KAN,00000000-0000-0000-0000-000000000002:");

        assertThat(stub.jiraProjectKey(ONE)).isEmpty();
        assertThat(stub.jiraProjectKey(TWO)).isEmpty();
        assertThat(new StubProjectAccess("", "").jiraProjectKey(ONE)).isEmpty();
    }
}
