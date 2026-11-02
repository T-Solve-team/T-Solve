package vn.tsolve.platform.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class WorkspaceHostTest {
    private final WorkspaceHost hosts = new WorkspaceHost("example.test");

    @Test
    void resolvesOnlyOneValidWorkspaceLabel_AC_002_2_AC_002_3_AC_002_4_AC_002_6_AC_002_10_AC_002_17() {
        assertThat(hosts.siteName("ONE.EXAMPLE.TEST:8443")).contains("one");
        assertThat(new WorkspaceHost("localhost").siteName("one.localhost:5173"))
                .contains("one");
        for (String invalid : new String[] {
            "example.test",
            "www.example.test",
            "staging.example.test",
            "login.example.test",
            "api.example.test",
            "admin.example.test",
            "a.b.example.test",
            "one.example.test.evil",
            "one-example.test",
            "one.example.test:65536",
            "one.example.test:0",
            "-one.example.test",
            "one_.example.test",
            "one.example.test/path",
            "one.example.test,evil.test",
            "one.example.test\r\nX-Header: one",
            "a".repeat(64) + ".example.test"
        }) {
            assertThat(hosts.siteName(invalid)).as(invalid).isEmpty();
        }
        assertThat(hosts.siteName(null)).isEmpty();
        assertThatThrownBy(() -> new WorkspaceHost("https://example.test"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
