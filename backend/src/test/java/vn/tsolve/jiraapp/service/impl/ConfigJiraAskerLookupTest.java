package vn.tsolve.jiraapp.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import vn.tsolve.common.security.TSolveUser;
import vn.tsolve.identity.account.service.AccountLookupService;

class ConfigJiraAskerLookupTest {

    private static final UUID ACCOUNT = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final TSolveUser USER = new TSolveUser(ACCOUNT, "an@t-solve.vn", "An", Set.of());

    private final AccountLookupService accounts = mock(AccountLookupService.class);

    @Test
    void mapsALinkedJiraAccountToItsActiveAccount_AC_010_11() {
        when(accounts.findActive(ACCOUNT)).thenReturn(Optional.of(USER));
        ConfigJiraAskerLookup lookup = new ConfigJiraAskerLookup("jira-1=" + ACCOUNT, accounts);

        assertThat(lookup.find("cloud-1", "jira-1")).contains(USER);
    }

    @Test
    void treatsAnUnknownJiraAccountAsNotLinked_AC_010_11() {
        ConfigJiraAskerLookup lookup = new ConfigJiraAskerLookup("jira-1=" + ACCOUNT, accounts);

        assertThat(lookup.find("cloud-1", "jira-2")).isEmpty();
        verifyNoInteractions(accounts);
    }

    @Test
    void treatsALinkToAnInactiveAccountAsNotLinked_AC_010_11() {
        when(accounts.findActive(ACCOUNT)).thenReturn(Optional.empty());
        ConfigJiraAskerLookup lookup = new ConfigJiraAskerLookup("jira-1=" + ACCOUNT, accounts);

        assertThat(lookup.find("cloud-1", "jira-1")).isEmpty();
    }

    @Test
    void linksNobodyWhenNothingIsConfigured_AC_010_11() {
        ConfigJiraAskerLookup lookup = new ConfigJiraAskerLookup("", accounts);

        assertThat(lookup.find("cloud-1", "jira-1")).isEmpty();
        verifyNoInteractions(accounts);
    }

    @Test
    void readsSeveralLinksWithSpacesAndATrailingSeparator() {
        UUID other = UUID.fromString("10000000-0000-0000-0000-000000000002");
        when(accounts.findActive(other)).thenReturn(Optional.of(USER));
        ConfigJiraAskerLookup lookup =
                new ConfigJiraAskerLookup(" jira-1 = " + ACCOUNT + " ; jira-2=" + other + ";", accounts);

        assertThat(lookup.find("cloud-1", "jira-2")).contains(USER);
    }

    @ParameterizedTest
    @ValueSource(strings = {"jira-1", "=10000000-0000-0000-0000-000000000001", "jira-1=", "jira-1=not-a-uuid"})
    void refusesToStartWithAMalformedLink(String config) {
        assertThatThrownBy(() -> new ConfigJiraAskerLookup(config, accounts))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("TSOLVE_STUB_JIRA_ASKERS");
    }
}
