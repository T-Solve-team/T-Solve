package vn.tsolve.jiraapp.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import vn.tsolve.common.security.TSolveUser;
import vn.tsolve.identity.dto.LinkedAccount;
import vn.tsolve.identity.service.JiraAccountLinkLookup;

class LinkedJiraAskerLookupTest {

    private static final TSolveUser USER =
            new TSolveUser(UUID.fromString("10000000-0000-0000-0000-000000000001"), "an@t-solve.vn", "An", Set.of());

    private final JiraAccountLinkLookup links = mock(JiraAccountLinkLookup.class);
    private final LinkedJiraAskerLookup lookup = new LinkedJiraAskerLookup(links);

    @Test
    void findsTheUserThroughIdentitysLink_AC_014_11() {
        when(links.find("jira-1")).thenReturn(Optional.of(new LinkedAccount(USER, Instant.EPOCH)));

        assertThat(lookup.find("cloud-1", "jira-1")).contains(USER);
    }

    @Test
    void treatsNoLinkOrALinkToAnAccountThatIsNotActiveAsNotLinked_AC_014_11() {
        // identity answers nothing for both (AC-001.77)
        when(links.find("jira-2")).thenReturn(Optional.empty());

        assertThat(lookup.find("cloud-1", "jira-2")).isEmpty();
    }

    @Test
    void noLongerReadsTheStubSetting_AC_014_11() {
        assertThat(LinkedJiraAskerLookup.class.getConstructors())
                .singleElement()
                .satisfies(constructor ->
                        assertThat(constructor.getParameterTypes()).containsExactly(JiraAccountLinkLookup.class));
    }
}
