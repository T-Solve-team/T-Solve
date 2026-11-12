package vn.tsolve.workspace.site;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class JiraSiteRuleTest {

    @DisplayName("AC-009.3 accepts Jira Cloud sites and normalises whitespace case and trailing slash")
    @ParameterizedTest(name = "AC-009.3 normalises {0} to {1}")
    @CsvSource(
            value = {
                "'https://acme.atlassian.net',https://acme.atlassian.net",
                "'  HTTPS://ACME.atlassian.net/ ',https://acme.atlassian.net",
                "'https://a.atlassian.net',https://a.atlassian.net",
                "'https://team-42.atlassian.net/',https://team-42.atlassian.net",
                "'https://42.atlassian.net',https://42.atlassian.net"
            },
            ignoreLeadingAndTrailingWhitespace = false)
    void normalisesValidSites(String raw, String expected) {
        assertThat(JiraSiteRule.normalise(raw)).contains(expected);
    }

    @DisplayName("AC-009.3 rejects unsafe or malformed site addresses")
    @ParameterizedTest(name = "AC-009.3 rejects site [{0}]")
    @NullAndEmptySource
    @ValueSource(
            strings = {
                "http://acme.atlassian.net",
                "https://localhost",
                "https://169.254.169.254",
                "https://acme.atlassian.net.evil.com",
                "https://x@acme.atlassian.net",
                "https://acme.atlassian.net:8443",
                "https://acme.atlassian.net:443",
                "https://acme.atlassian.net:",
                "https://a.b.atlassian.net",
                "https://acme.atlassian.net/jira",
                "https://acme.atlassian.net?x=1",
                "https://acme.atlassian.net?",
                "https://acme.atlassian.net#f",
                "https://acme.atlassian.net#",
                "https://atlassian.net",
                "https://-acme.atlassian.net",
                "https://acme-.atlassian.net",
                "https://ac_me.atlassian.net",
                "https://acme.atlassian.net.",
                "https://acme%2eatlassian.net",
                "https://acme.atlassian.net/%2f",
                "https://acme.atlassian.net/../",
                "https://acme.atlassian.net/\\evil.com",
                "https://acme.atlassian.net/%",
                "https:///acme.atlassian.net",
                "https://ac me.atlassian.net",
                "   ",
                "garbage"
            })
    void rejectsInvalidSites(String raw) {
        assertThat(JiraSiteRule.normalise(raw)).isEmpty();
    }
}
