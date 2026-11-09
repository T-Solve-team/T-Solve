package vn.tsolve.identity.signin;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Task 31: the T-Solve theme on Keycloak's login, error and logout pages (AC-001.67 first half, AC-001.68), against a
 * real Keycloak with the committed realm. The three pages are fetched once: the login page with an unknown hint, the
 * error page after the upstream token endpoint answers 500, and the logout confirm page. On Docker 29 run with
 * {@code -DargLine=-Dapi.version=1.44} (never committed).
 */
class KeycloakThemeIT {

    /** {@code tsolve-web.baseUrl} in the dev realm (Q1 = A). */
    private static final String BACK_URL = "http://localhost:5173/";

    private static final String BACK_TEXT = "Back to sign-in";

    private static KeycloakItStack stack;
    private static String origin;
    private static final Map<String, TestBrowser.Reply> PAGES = new java.util.LinkedHashMap<>();
    private static TestBrowser browser;

    @BeforeAll
    static void fetchThePages() {
        stack = KeycloakItStack.get();
        origin = stack.loginOrigin();
        browser = stack.newBrowser();

        TestBrowser b = stack.newBrowser();
        String unknownHint = UriComponentsBuilder.fromUriString(
                        stack.start(b, "acme").location())
                .replaceQueryParam("kc_idp_hint", "google-unknown")
                .build(true)
                .toUriString();
        PAGES.put("login", b.followUntilSettled(b.get(unknownHint), stack.callbackBase("acme"), false));

        List<String> stubs = new ArrayList<>();
        try {
            stubs.add(stack.addFakeGoogleStub(stack.chooserStub("boom")));
            stubs.add(stack.addFakeGoogleStub(stack.tokenStub("boom", 500)));
            TestBrowser e = stack.newBrowser();
            PAGES.put("error", e.followUntilSettled(stack.start(e, "acme"), stack.callbackBase("acme"), true));
        } finally {
            stack.removeFakeGoogleStubs(stubs);
        }

        TestBrowser l = stack.newBrowser();
        PAGES.put(
                "logout",
                l.followUntilSettled(
                        l.get(origin + "/realms/" + KeycloakItStack.REALM + "/protocol/openid-connect/logout"),
                        stack.callbackBase("acme"),
                        false));
    }

    @Test
    @DisplayName("AC-001.67 the realm tsolve uses the T-Solve theme on its login, error and logout pages")
    void realmUsesTheTsolveThemeOnLoginErrorAndLogoutPages_AC_001_67() {
        assertThat(stack.realm().toRepresentation().getLoginTheme())
                .as("realm loginTheme")
                .isEqualTo("tsolve");

        PAGES.forEach((name, reply) -> {
            String css = ThemePageScan.themeStylesheet(reply.body());
            assertThat(css)
                    .as("%s page (status %d) links tsolve.css", name, reply.status())
                    .isNotNull();
            TestBrowser.Reply sheet = browser.get(css.startsWith("/") ? origin + css : css);
            assertThat(sheet.status()).as("%s page: GET %s", name, css).isEqualTo(200);
            assertThat(sheet.header("content-type")).startsWith("text/css");
            assertThat(ThemePageScan.hasInlineLogo(reply.body()))
                    .as("%s page shows the T-Solve logo", name)
                    .isTrue();
        });
    }

    @Test
    @DisplayName("AC-001.68 the themed pages load no script and no resource from another host")
    void themedPagesLoadNoScriptAndNoForeignResource_AC_001_68() {
        PAGES.forEach((name, reply) -> {
            String html = reply.body();
            assertThat(ThemePageScan.hasScript(html))
                    .as("%s page has <script", name)
                    .isFalse();
            assertThat(ThemePageScan.hasEventHandlerAttribute(html))
                    .as("%s page has an on...= attribute", name)
                    .isFalse();
            assertThat(ThemePageScan.hasJavascriptUrl(html))
                    .as("%s page has a javascript: URL", name)
                    .isFalse();
            assertThat(ThemePageScan.resourceUrls(html))
                    .as("%s page resources", name)
                    .allSatisfy(url -> assertThat(ThemePageScan.isOwnOrigin(url, origin))
                            .as("%s is on Keycloak's origin", url)
                            .isTrue());
        });

        String css = ThemePageScan.themeStylesheet(PAGES.get("error").body());
        assertThat(css).as("the error page links tsolve.css").isNotNull();
        String sheet = browser.get(css.startsWith("/") ? origin + css : css).body();
        assertThat(ThemePageScan.cssUrls(sheet))
                .as("tsolve.css urls and imports")
                .allSatisfy(url -> assertThat(ThemePageScan.isOwnOrigin(url, origin))
                        .as("%s is on Keycloak's origin", url)
                        .isTrue());
        assertThat(sheet).as("tsolve.css has no script").doesNotContainIgnoringCase("javascript:");
    }

    @Test
    @DisplayName("AC-001.68 the themed pages link to no Workspace site except Back to sign-in")
    void themedPagesLinkToNoWorkspaceSiteExceptBackToSignIn_AC_001_68() {
        assertThat(ThemePageScan.isOwnOrigin(origin + "/realms/x", origin)).isTrue();
        assertThat(ThemePageScan.isOwnOrigin(origin + ".evil.test/x", origin)).isFalse();
        PAGES.forEach((name, reply) -> {
            String html = reply.body();
            assertThat(ThemePageScan.formActions(html))
                    .as("%s page form actions", name)
                    .allSatisfy(url -> assertThat(ThemePageScan.isOwnOrigin(url, origin))
                            .as("%s is on Keycloak's origin", url)
                            .isTrue());
            List<ThemePageScan.Link> foreign = ThemePageScan.links(html).stream()
                    .filter(link -> !ThemePageScan.isOwnOrigin(link.href(), origin))
                    .toList();
            assertThat(foreign).as("%s page: links off Keycloak's origin", name).hasSizeLessThanOrEqualTo(1);
            foreign.forEach(link -> assertThat(link.text()).isEqualTo(BACK_TEXT));
        });

        List<ThemePageScan.Link> back = ThemePageScan.links(PAGES.get("error").body()).stream()
                .filter(link -> BACK_TEXT.equals(link.text()))
                .toList();
        assertThat(back)
                .as("the error page has exactly one '%s' link", BACK_TEXT)
                .hasSize(1);
        assertThat(back.get(0).href()).isEqualTo(BACK_URL);
    }
}
