package vn.tsolve.workspace.site;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.headerDoesNotExist;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.net.SocketTimeoutException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

@ExtendWith(OutputCaptureExtension.class)
class InterimJiraSiteLinkerTest {

    private static final String SITE = "https://acme.atlassian.net";
    private static final String TENANT_INFO = SITE + "/_edge/tenant_info";

    private MockRestServiceServer jira;
    private JiraSiteLinker linker;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        jira = MockRestServiceServer.bindTo(builder).build();
        linker = new InterimJiraSiteLinker(builder);
    }

    @Test
    @DisplayName("AC-007.4 returns the cloudId from public tenant information without credentials")
    void returnsCloudId() {
        jira.expect(requestTo(TENANT_INFO))
                .andExpect(method(HttpMethod.GET))
                .andExpect(headerDoesNotExist("Authorization"))
                .andRespond(withSuccess("{\"cloudId\":\"fake-cloud-id\"}", MediaType.APPLICATION_JSON));

        assertThat(linker.linkJiraSite(SITE)).isEqualTo("fake-cloud-id");
        jira.verify();
    }

    @DisplayName("AC-007.4 refuses every non-200 status and logs only host and status")
    @ParameterizedTest(name = "AC-007.4 refuses HTTP {0}")
    @ValueSource(ints = {201, 204, 301, 302, 307, 308, 400, 401, 403, 404, 429, 500})
    void refusesNon200(int status, CapturedOutput output) {
        jira.expect(requestTo(TENANT_INFO))
                .andRespond(withStatus(HttpStatus.valueOf(status))
                        .body("fake-private-body fake-token")
                        .contentType(MediaType.APPLICATION_JSON));

        assertUnreadable();
        assertThat(output)
                .contains("acme.atlassian.net", "status=" + status)
                .doesNotContain("fake-private-body", "fake-token");
    }

    @Test
    @DisplayName("AC-007.4 refuses a 302 without following its Location")
    void refusesRedirect() {
        jira.expect(requestTo(TENANT_INFO))
                .andRespond(withStatus(HttpStatus.FOUND)
                        .location(java.net.URI.create("https://169.254.169.254/fake-private-target")));

        assertUnreadable();
    }

    @DisplayName("AC-007.4 refuses absent or blank cloudId")
    @ParameterizedTest(name = "AC-007.4 refuses tenant information [{0}]")
    @ValueSource(strings = {"", "{}", "{\"cloudId\":null}", "{\"cloudId\":\"\"}", "{\"cloudId\":\"  \"}"})
    void refusesMissingCloudId(String body) {
        jira.expect(requestTo(TENANT_INFO)).andRespond(withSuccess(body, MediaType.APPLICATION_JSON));

        assertUnreadable();
    }

    @Test
    @DisplayName("AC-007.4 refuses IO failures without exposing their messages or causes")
    void refusesIoFailure(CapturedOutput output) {
        jira.expect(requestTo(TENANT_INFO)).andRespond(request -> {
            throw new SocketTimeoutException("fake-private-body fake-token");
        });

        assertUnreadable();
        assertThat(output).contains("acme.atlassian.net", "status=0").doesNotContain("fake-private-body", "fake-token");
    }

    @Test
    @DisplayName("AC-007.4 refuses malformed tenant information without exposing its body")
    void refusesMalformedBody(CapturedOutput output) {
        jira.expect(requestTo(TENANT_INFO))
                .andRespond(withSuccess("fake-private-body fake-token", MediaType.APPLICATION_JSON));

        assertUnreadable();
        assertThat(output).doesNotContain("fake-private-body", "fake-token");
    }

    @DisplayName("AC-007.3 refuses unchecked or non-normalised input before any outbound call")
    @ParameterizedTest(name = "AC-007.3 makes no request for [{0}]")
    @NullAndEmptySource
    @ValueSource(
            strings = {
                "  HTTPS://ACME.atlassian.net/ ",
                "https://acme.atlassian.net/",
                "https://ACME.atlassian.net",
                "http://acme.atlassian.net",
                "https://localhost",
                "https://169.254.169.254",
                "https://acme.atlassian.net.evil.com",
                "https://fake-token@acme.atlassian.net",
                "garbage"
            })
    void refusesUncheckedInput(String raw, CapturedOutput output) {
        assertThatThrownBy(() -> linker.linkJiraSite(raw))
                .isInstanceOfSatisfying(JiraSiteLinkRefusedException.class, error -> assertThat(error.getReason())
                        .isEqualTo(JiraSiteLinkRefusedException.Reason.UNREADABLE))
                .hasMessage("Jira site link refused: UNREADABLE")
                .hasNoCause();
        assertThat(output).doesNotContain("fake-token");
        jira.verify();
    }

    private void assertUnreadable() {
        assertThatThrownBy(() -> linker.linkJiraSite(SITE))
                .isInstanceOfSatisfying(JiraSiteLinkRefusedException.class, error -> assertThat(error.getReason())
                        .isEqualTo(JiraSiteLinkRefusedException.Reason.UNREADABLE))
                .hasMessage("Jira site link refused: UNREADABLE")
                .hasNoCause();
        jira.verify();
    }
}
