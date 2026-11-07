package vn.tsolve.tracker.service.impl;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import vn.tsolve.tracker.dto.JiraAccount;

class ConfiguredJiraServiceAccountTest {

    @Test
    void readsTheConfiguredServiceAccount_AC_012_1() {
        var account = new ConfiguredJiraServiceAccount("https://acme.atlassian.net/", " bot@acme.test ", "secret-token")
                .current();

        assertThat(account).contains(new JiraAccount("https://acme.atlassian.net", "bot@acme.test", "secret-token"));
    }

    @Test
    void hasNoAccountWhenAnyValueIsMissing_AC_012_8() {
        assertThat(new ConfiguredJiraServiceAccount("", "bot@acme.test", "t").current())
                .isEmpty();
        assertThat(new ConfiguredJiraServiceAccount("https://acme.atlassian.net", null, "t").current())
                .isEmpty();
        assertThat(new ConfiguredJiraServiceAccount("https://acme.atlassian.net", "bot@acme.test", "  ").current())
                .isEmpty();
    }

    @Test
    void neverPrintsTheEmailOrTheToken_NF_9() {
        String printed = new JiraAccount("https://acme.atlassian.net", "bot@acme.test", "secret-token").toString();

        assertThat(printed).doesNotContain("bot@acme.test").doesNotContain("secret-token");
    }
}
