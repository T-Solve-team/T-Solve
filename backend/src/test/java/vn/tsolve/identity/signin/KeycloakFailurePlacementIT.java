package vn.tsolve.identity.signin;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestReporter;
import org.keycloak.representations.idm.RealmRepresentation;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Task 31 (Q6): where each failure Keycloak cannot hand back lands, and the control case that does reach the callback.
 * Each test starts a real sign-in on {@code acme.localhost:8080}, causes one failure, follows redirects to the first
 * answer that is not a 3xx (or to the callback), records a row (case, host, status, place) and asserts the place the
 * spec names. The table is printed at the end. On Docker 29 run with {@code -DargLine=-Dapi.version=1.44}.
 */
class KeycloakFailurePlacementIT {

    private static final String MSG06_TEXT = "Something went wrong. Please try again later.";
    private static final String BACK_TEXT = "Back to sign-in";
    private static final String BACK_URL = "http://localhost:5173/";
    private static final String THEMED = "themed page";
    private static final String CALLBACK = "callback";
    private static final String UPSTREAM = "upstream page";
    private static final List<String> TABLE = new ArrayList<>();

    private static KeycloakItStack stack;

    @BeforeAll
    static void startStack() {
        stack = KeycloakItStack.get();
    }

    @AfterAll
    static void printTable() {
        System.out.println("=== Task 31 failure placement (case | final host | status | place) ===");
        TABLE.forEach(System.out::println);
        System.out.println("=== end ===");
    }

    // ---- helpers

    private static String hostOf(String url) {
        return URI.create(url).getHost();
    }

    /** Where the settled answer is: the callback (302 to it), Keycloak's origin, or the fake Google host. */
    private static String placeOf(TestBrowser browser, TestBrowser.Reply reply) {
        String location = reply.location();
        if (reply.status() / 100 == 3 && location != null && location.startsWith(stack.callbackBase("acme"))) {
            return CALLBACK;
        }
        String url = browser.lastUrl();
        if (url.startsWith(stack.loginOrigin())) {
            return THEMED;
        }
        return hostOf(url).startsWith("fake-google") ? UPSTREAM : hostOf(url);
    }

    private static String record(
            TestReporter reporter, String name, TestBrowser browser, TestBrowser.Reply reply, String place) {
        String row = name + " | " + (place.equals(CALLBACK) ? "acme.localhost" : hostOf(browser.lastUrl())) + " | "
                + reply.status() + " | " + place + " | "
                + (place.equals(CALLBACK)
                        ? reply.location()
                        : ThemePageScan.visibleText(reply.body()).replaceAll("^(.{0,110}).*$", "$1"));
        TABLE.add(row);
        reporter.publishEntry(name, row);
        return row;
    }

    private static void assertThemedFailurePage(TestBrowser.Reply reply) {
        assertThemedFailurePage(reply, BACK_URL);
    }

    private static void assertThemedFailurePage(TestBrowser.Reply reply, String backUrl) {
        String text = ThemePageScan.visibleText(reply.body());
        assertThat(ThemePageScan.count(text, MSG06_TEXT))
                .as("MSG06 text appears once on the page")
                .isEqualTo(1);
        List<ThemePageScan.Link> back = ThemePageScan.links(reply.body()).stream()
                .filter(link -> BACK_TEXT.equals(link.text()))
                .toList();
        assertThat(back).as("'%s' link", BACK_TEXT).hasSize(1);
        assertThat(back.get(0).href()).isEqualTo(backUrl);
    }

    private static TestBrowser.Reply settle(TestBrowser browser, TestBrowser.Reply first, boolean click) {
        return browser.followUntilSettled(first, stack.callbackBase("acme"), click);
    }

    private static Set<String> authRequestKeys() {
        Set<String> keys = stack.redis().keys(RedisAuthorizationRequestRepository.KEY_PREFIX + "*");
        return keys == null ? Set.of() : keys;
    }

    // ---- tests

    @Test
    @DisplayName("AC-001.67 edge 118 recording: a Google redirect_uri_mismatch lands on the upstream page")
    void redirectUriMismatchStaysOnTheUpstreamPage_AC_001_67_edge118(TestReporter reporter) {
        List<String> stubs = new ArrayList<>();
        try {
            stubs.add(stack.addFakeGoogleStub(stack.authorizeStub(
                    400,
                    Map.of("Content-Type", "text/html; charset=utf-8"),
                    "<!doctype html><html><body><h1>Error 400: redirect_uri_mismatch</h1></body></html>")));
            TestBrowser browser = stack.newBrowser();

            TestBrowser.Reply reply = settle(browser, stack.start(browser, "acme"), false);

            String place = placeOf(browser, reply);
            record(reporter, "redirect_uri_mismatch", browser, reply, place);
            assertThat(place).isEqualTo(UPSTREAM);
            assertThat(reply.status()).isEqualTo(400);
            assertThat(reply.body()).contains("redirect_uri_mismatch");
        } finally {
            stack.removeFakeGoogleStubs(stubs);
        }
    }

    @Test
    @DisplayName("AC-001.67 an expired Keycloak login session lands on the themed page with MSG06")
    void expiredLoginSessionLandsOnTheThemedPageWithMsg06_AC_001_67(TestReporter reporter) throws Exception {
        RealmRepresentation before = stack.realm().toRepresentation();
        Integer lifespanLogin = before.getAccessCodeLifespanLogin();
        Integer lifespanUserAction = before.getAccessCodeLifespanUserAction();
        try {
            RealmRepresentation shortLived = stack.realm().toRepresentation();
            shortLived.setAccessCodeLifespanLogin(2);
            shortLived.setAccessCodeLifespanUserAction(2);
            stack.realm().update(shortLived);
            TestBrowser browser = stack.newBrowser();
            TestBrowser.Reply chooser = settle(browser, stack.start(browser, "acme"), false);
            assertThat(chooser.status()).as("the fake chooser is shown").isEqualTo(200);
            String link = firstHref(chooser.body());
            Thread.sleep(4000);

            TestBrowser.Reply reply = settle(browser, browser.get(link), false);

            String place = placeOf(browser, reply);
            record(reporter, "expired login session", browser, reply, place);
            assertThat(place).isEqualTo(THEMED);
            assertThemedFailurePage(reply);
        } finally {
            RealmRepresentation restore = stack.realm().toRepresentation();
            restore.setAccessCodeLifespanLogin(lifespanLogin);
            restore.setAccessCodeLifespanUserAction(lifespanUserAction);
            stack.realm().update(restore);
        }
    }

    @Test
    @DisplayName("AC-001.67 edge 104 an unknown kc_idp_hint lands on the themed login page with MSG06")
    void unknownIdpHintLandsOnTheThemedLoginPageWithMsg06_AC_001_67_edge104(TestReporter reporter) {
        TestBrowser browser = stack.newBrowser();
        String unknownHint = UriComponentsBuilder.fromUriString(
                        stack.start(browser, "acme").location())
                .replaceQueryParam("kc_idp_hint", "google-unknown")
                .build(true)
                .toUriString();

        TestBrowser.Reply reply = settle(browser, browser.get(unknownHint), false);

        String place = placeOf(browser, reply);
        record(reporter, "unknown kc_idp_hint", browser, reply, place);
        assertThat(place).isEqualTo(THEMED);
        assertThemedFailurePage(reply);
        String html = reply.body().toLowerCase();
        assertThat(html).doesNotContain("type=\"password\"").doesNotContain("google-acme");
    }

    @Test
    @DisplayName("AC-001.67 a Keycloak internal error lands on the themed error page with MSG06")
    void internalErrorLandsOnTheThemedErrorPageWithMsg06_AC_001_67(TestReporter reporter) {
        List<String> stubs = new ArrayList<>();
        try {
            stubs.add(stack.addFakeGoogleStub(stack.chooserStub("boom")));
            stubs.add(stack.addFakeGoogleStub(stack.tokenStub("boom", 500)));
            TestBrowser browser = stack.newBrowser();

            TestBrowser.Reply reply = settle(browser, stack.start(browser, "acme"), true);

            String place = placeOf(browser, reply);
            record(reporter, "internal error (token 500)", browser, reply, place);
            assertThat(place).isEqualTo(THEMED);
            assertThemedFailurePage(reply);
        } finally {
            stack.removeFakeGoogleStubs(stubs);
        }
    }

    @Test
    @DisplayName("AC-001.67 a cancel at Google lands on the themed page with MSG06")
    void cancelAtGoogleLandsOnTheThemedPageWithMsg06_AC_001_67(TestReporter reporter) {
        List<String> stubs = new ArrayList<>();
        try {
            stubs.add(stack.addFakeGoogleStub(stack.authorizeStub(
                    302,
                    Map.of(
                            "Location",
                            "{{request.query.redirect_uri.[0]}}?error=access_denied&state={{request.query.state.[0]}}"),
                    "")));
            TestBrowser browser = stack.newBrowser();

            TestBrowser.Reply reply = settle(browser, stack.start(browser, "acme"), false);

            String place = placeOf(browser, reply);
            record(reporter, "access_denied (cancel at Google)", browser, reply, place);
            assertThat(place).as("never reaches the callback").isNotEqualTo(CALLBACK);
            assertThat(place).isEqualTo(THEMED);
            assertThemedFailurePage(reply);
        } finally {
            stack.removeFakeGoogleStubs(stubs);
        }
    }

    @Test
    @DisplayName("AC-001.67 when Keycloak does not know the client the link falls back to the theme URL")
    void unknownClientFallsBackToTheThemeUrl_AC_001_67(TestReporter reporter) {
        TestBrowser browser = stack.newBrowser();
        String auth = stack.loginOrigin() + "/realms/" + KeycloakItStack.REALM
                + "/protocol/openid-connect/auth?client_id=unknown&response_type=code&scope=openid"
                + "&redirect_uri=http%3A%2F%2Flocalhost%3A5173%2Fcb";

        TestBrowser.Reply reply = settle(browser, browser.get(auth), false);

        String place = placeOf(browser, reply);
        record(reporter, "unknown client_id", browser, reply, place);
        assertThat(place).isEqualTo(THEMED);
        assertThat(KeycloakItStack.FALLBACK_URL)
                .as("the fallback differs from the client URL")
                .isNotEqualTo(BACK_URL);
        assertThemedFailurePage(reply, KeycloakItStack.FALLBACK_URL);
    }

    private static String firstHref(String html) {
        java.util.regex.Matcher link =
                java.util.regex.Pattern.compile("<a[^>]*href=\"([^\"]+)\"").matcher(html);
        assertThat(link.find()).as("the chooser has a link").isTrue();
        return link.group(1).replace("&amp;", "&");
    }
}
