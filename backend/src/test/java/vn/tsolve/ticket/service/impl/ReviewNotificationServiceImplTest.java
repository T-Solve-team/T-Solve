package vn.tsolve.ticket.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import vn.tsolve.ticket.repository.TicketDecisionRepository;
import vn.tsolve.tracker.dto.JiraAccount;
import vn.tsolve.tracker.service.JiraServiceAccount;

class ReviewNotificationServiceImplTest {

    private final JiraServiceAccount jira = mock(JiraServiceAccount.class);
    private final ReviewNotificationServiceImpl service =
            new ReviewNotificationServiceImpl(mock(TicketDecisionRepository.class), jira);

    private void site(String siteUrl) {
        when(jira.current()).thenReturn(Optional.of(new JiraAccount(siteUrl, "cloud-id", "token")));
    }

    @ParameterizedTest
    @ValueSource(
            strings = {"https://acme.atlassian.net", "https://acme.atlassian.net/", " https://acme.atlassian.net// "})
    void givesTheServiceAccountSiteWithoutTrailingSlashes_AC_010_8(String siteUrl) {
        site(siteUrl);

        assertThat(service.jiraSiteUrl()).contains("https://acme.atlassian.net");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "http://acme.atlassian.net", "acme.atlassian.net", "https://"})
    void givesNoSiteForAnUnusableSiteUrl_AC_010_8(String siteUrl) {
        site(siteUrl);

        assertThat(service.jiraSiteUrl()).isEmpty();
    }

    @Test
    void givesNoSiteWithoutAServiceAccount_AC_010_8() {
        when(jira.current()).thenReturn(Optional.empty());

        assertThat(service.jiraSiteUrl()).isEmpty();
    }
}
