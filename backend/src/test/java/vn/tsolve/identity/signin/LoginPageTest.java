package vn.tsolve.identity.signin;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

class LoginPageTest {

    private static final Pattern BODY = Pattern.compile("(?is)<body[^>]*>(.*)</body>");

    private static ResponseEntity<String> msg141Page() {
        return LoginPage.response(HttpStatus.BAD_REQUEST, "MSG141");
    }

    @Test
    @DisplayName("AC-001.39 shows exactly the Report3 text of MSG141 and nothing else")
    void showsExactlyTheMsg141Text_AC_001_39() {
        ResponseEntity<String> page = msg141Page();

        assertThat(page.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        Matcher body = BODY.matcher(page.getBody());
        assertThat(body.find()).as("the page has a <body>").isTrue();
        String text =
                body.group(1).replaceAll("<[^>]+>", " ").replaceAll("\\s+", " ").trim();
        assertThat(text).isEqualTo(SignInTestFixtures.MSG141_TEXT);
    }

    @Test
    @DisplayName("AC-001.39 has no link to any site")
    void hasNoLinkToAnySite_AC_001_39() {
        String html = msg141Page().getBody().toLowerCase(Locale.ROOT);

        assertThat(html)
                .doesNotContain("<a")
                .doesNotContain("href")
                .doesNotContain("<form")
                .doesNotContain("http-equiv=\"refresh\"")
                .doesNotContain("http://")
                .doesNotContain("https://")
                .doesNotContain("localhost")
                .doesNotContain("tsolve.io.vn");
    }

    @Test
    @DisplayName("AC-001.39 does not load the web app")
    void doesNotLoadTheWebApp_AC_001_39() {
        String html = msg141Page().getBody().toLowerCase(Locale.ROOT);

        assertThat(html)
                .doesNotContain("<script")
                .doesNotContain("src=")
                .doesNotContain("<link")
                .doesNotContain("<iframe")
                .doesNotContain("<base")
                .doesNotContain("<object")
                .doesNotContain("<embed");
    }

    @Test
    @DisplayName("AC-001.39 is sent as a locked-down, uncached HTML page")
    void isSentAsLockedDownUncachedHtml_AC_001_39() {
        HttpHeaders headers = msg141Page().getHeaders();

        assertThat(headers.getContentType()).isEqualTo(MediaType.parseMediaType("text/html;charset=UTF-8"));
        assertThat(headers.getFirst("Content-Security-Policy"))
                .isEqualTo("default-src 'none'; style-src 'unsafe-inline'");
        assertThat(headers.getCacheControl()).isEqualTo("no-store");
    }
}
