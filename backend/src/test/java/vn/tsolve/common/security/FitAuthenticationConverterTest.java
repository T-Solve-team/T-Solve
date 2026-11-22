package vn.tsolve.common.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

class FitAuthenticationConverterTest {

    private final FitAuthenticationConverter converter = new FitAuthenticationConverter();

    @Test
    void readsTheProjectOfAJiraProjectPageCall_AC_010_13() {
        var principal = principal(Map.of(
                "cloudId",
                "cloud-1",
                "extension",
                Map.of("type", "jira:projectPage", "project", Map.of("id", "10001", "key", "DP"))));

        assertThat(principal)
                .isEqualTo(new JiraAppPrincipal(
                        "cloud-1", "account-1", "https://api.atlassian.com/ex/jira/cloud-1", "DP"));
    }

    @Test
    void hasNoProjectWhenTheModuleHasNone_AC_010_13() {
        assertThat(principal(Map.of("cloudId", "cloud-1")).projectKey()).isNull();
        assertThat(principal(Map.of("cloudId", "cloud-1", "extension", Map.of("type", "jira:issuePanel")))
                        .projectKey())
                .isNull();
        assertThat(principal(Map.of("cloudId", "cloud-1", "extension", Map.of("project", Map.of("key", " "))))
                        .projectKey())
                .isNull();
    }

    private JiraAppPrincipal principal(Map<String, Object> context) {
        Jwt fit = Jwt.withTokenValue("fit")
                .header("alg", "RS256")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60))
                .claim("principal", "account-1")
                .claim("app", Map.of("apiBaseUrl", "https://api.atlassian.com/ex/jira/cloud-1"))
                .claim("context", context)
                .build();
        return (JiraAppPrincipal) converter.convert(fit).getPrincipal();
    }
}
