package vn.tsolve.identity.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import vn.tsolve.common.security.TSolveUser;
import vn.tsolve.identity.account.service.AccountLookupService;
import vn.tsolve.identity.dto.LinkedAccount;
import vn.tsolve.identity.jiralink.JiraAccountLink;
import vn.tsolve.identity.jiralink.JiraAccountLinkRepository;

class JiraAccountLinkLookupImplTest {

    private static final UUID ACCOUNT = UUID.fromString("7c0a3e7e-1b8a-4c1e-9a55-3f2d1c6b9e01");
    private static final Instant LINKED_AT = Instant.parse("2026-11-17T08:00:00Z");

    private final JiraAccountLinkRepository links = mock(JiraAccountLinkRepository.class);
    private final AccountLookupService accounts = mock(AccountLookupService.class);
    private final JiraAccountLinkLookupImpl lookup = new JiraAccountLinkLookupImpl(links, accounts);

    @Test
    void answersTheActiveLinkedAccountWithTheLinkTime_AC_001_77() {
        TSolveUser user = new TSolveUser(ACCOUNT, "an@acme.example", "An Nguyen", Set.of());
        when(links.find("jira-1")).thenReturn(Optional.of(new JiraAccountLink("jira-1", ACCOUNT, LINKED_AT)));
        when(accounts.findActive(ACCOUNT)).thenReturn(Optional.of(user));

        assertThat(lookup.find("jira-1")).contains(new LinkedAccount(user, LINKED_AT));
    }

    @Test
    void answersNothingWithoutALink_AC_001_77() {
        when(links.find("jira-1")).thenReturn(Optional.empty());

        assertThat(lookup.find("jira-1")).isEmpty();
        verifyNoInteractions(accounts);
    }

    @Test
    void answersNothingForAnAccountThatIsNotActive_AC_001_77() {
        when(links.find("jira-1")).thenReturn(Optional.of(new JiraAccountLink("jira-1", ACCOUNT, LINKED_AT)));
        when(accounts.findActive(ACCOUNT)).thenReturn(Optional.empty());

        assertThat(lookup.find("jira-1")).isEmpty();
        verify(accounts).findActive(ACCOUNT);
    }

    @Test
    void answersNothingForABlankTrackerAccountWithoutReading_AC_001_77() {
        assertThat(lookup.find(null)).isEmpty();
        assertThat(lookup.find("  ")).isEmpty();
        verifyNoInteractions(links, accounts);
    }
}
