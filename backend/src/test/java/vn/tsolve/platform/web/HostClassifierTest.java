package vn.tsolve.platform.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/** Task 10a (KAN-258): which kind of host a raw {@code Host} header names. */
class HostClassifierTest {

    private static final String TEMPLATE = "https://{slug}.tsolve.io.vn";
    private static final String STAGING_TEMPLATE = "https://{slug}.staging.tsolve.io.vn";
    private static final String LOCAL_TEMPLATE = "http://{slug}.localhost:5173";
    private static final String VARIABLE = "TSOLVE_SITE_ORIGIN_TEMPLATE";

    private static final HostKind NOT_A_SITE = new HostKind.NotASite();
    private static final HostKind LOGIN = new HostKind.Login();
    private static final HostKind API = new HostKind.Api();
    private static final HostKind ADMIN = new HostKind.Admin();

    private static HostKind site(String label) {
        return new HostKind.Site(label);
    }

    static Stream<Arguments> hostVariants() {
        String longest = "a".repeat(63);
        return Stream.of(
                arguments("ACME.tsolve.io.vn:443", site("acme")),
                arguments("Acme.TSOLVE.IO.VN", site("acme")),
                arguments("acme.tsolve.io.vn:8080", site("acme")),
                arguments("xn--bcher-kva.tsolve.io.vn", site("xn--bcher-kva")),
                arguments(longest + ".tsolve.io.vn", site(longest)),
                arguments("a".repeat(64) + ".tsolve.io.vn", NOT_A_SITE),
                arguments("-acme.tsolve.io.vn", NOT_A_SITE),
                arguments("acme-.tsolve.io.vn", NOT_A_SITE),
                arguments("a_b.tsolve.io.vn", NOT_A_SITE),
                arguments("ac%6De.tsolve.io.vn", NOT_A_SITE),
                arguments(".tsolve.io.vn", NOT_A_SITE),
                arguments("acme.tsolve.io.vn.", NOT_A_SITE));
    }

    @ParameterizedTest(name = "{0} is {1}")
    @MethodSource("hostVariants")
    @DisplayName("AC-002.3 AC-002.4 edge 19 reads the host without case and port, and only an RFC 1123 label is a site")
    void classifiesHostVariants_AC_002_3_AC_002_4(String host, HostKind expected) {
        assertThat(HostClassifier.fromTemplate(TEMPLATE).classify(host)).isEqualTo(expected);
    }

    static Stream<Arguments> nonSiteHosts() {
        Stream<Arguments> noSite = Stream.of(
                        "tsolve.io.vn",
                        "tsolve.io.vn:443",
                        "www.tsolve.io.vn",
                        "staging.tsolve.io.vn",
                        "WWW.tsolve.io.vn",
                        "a.b.tsolve.io.vn",
                        "acme.example.com",
                        "acmetsolve.io.vn",
                        "acme.tsolve.io.vn.evil.com",
                        "localhost:8080",
                        "[::1]:8080",
                        null,
                        "",
                        "   ")
                .map(host -> arguments(TEMPLATE, host, NOT_A_SITE));
        Stream<Arguments> reserved = Stream.of(
                arguments(TEMPLATE, "login.tsolve.io.vn", LOGIN),
                arguments(TEMPLATE, "LOGIN.TSolve.io.vn:443", LOGIN),
                arguments(TEMPLATE, "api.tsolve.io.vn", API),
                arguments(TEMPLATE, "Api.tsolve.io.vn:8080", API),
                arguments(TEMPLATE, "admin.tsolve.io.vn", ADMIN),
                arguments(TEMPLATE, "ADMIN.tsolve.io.vn:443", ADMIN));
        Stream<Arguments> local = Stream.of(
                arguments(LOCAL_TEMPLATE, "acme.localhost", site("acme")),
                arguments(LOCAL_TEMPLATE, "acme.localhost:5173", site("acme")),
                arguments(LOCAL_TEMPLATE, "acme.localhost:80", site("acme")),
                arguments(LOCAL_TEMPLATE, "localhost:8080", NOT_A_SITE),
                arguments(LOCAL_TEMPLATE, "login.localhost:80", LOGIN));
        return Stream.concat(noSite, Stream.concat(reserved, local));
    }

    @ParameterizedTest(name = "{0}: {1} is {2}")
    @MethodSource("nonSiteHosts")
    @DisplayName(
            "AC-002.6 AC-002.10 AC-002.17 edge 19 tells the hosts that are no Workspace site, login, api and admin")
    void classifiesNonSiteHosts_AC_002_6_AC_002_10_AC_002_17(String template, String host, HostKind expected) {
        assertThat(HostClassifier.fromTemplate(template).classify(host)).isEqualTo(expected);
    }

    /** A null base domain means the template is refused. */
    static Stream<Arguments> templates() {
        Stream<Arguments> accepted = Stream.of(
                arguments(TEMPLATE, "tsolve.io.vn", List.of()),
                arguments(
                        STAGING_TEMPLATE,
                        "staging.tsolve.io.vn",
                        List.of("staging.staging.tsolve.io.vn", "acme.tsolve.io.vn")),
                arguments(LOCAL_TEMPLATE, "localhost", List.of()),
                arguments("https://{slug}.TSolve.io.vn/", "tsolve.io.vn", List.of()));
        Stream<Arguments> refused = Stream.of(
                        null,
                        "",
                        "   ",
                        "https://tsolve.io.vn",
                        "https://{slug}.{slug}.tsolve.io.vn",
                        "https://{slug}.tsolve .io.vn",
                        "https://app-{slug}.tsolve.io.vn",
                        "https://x.{slug}.tsolve.io.vn",
                        "https://{slug}.tsolve.io.vn/%zz")
                .map(template -> arguments(template, null, List.of()));
        return Stream.concat(accepted, refused);
    }

    @ParameterizedTest(name = "template {0} gives {1}")
    @MethodSource("templates")
    @DisplayName("AC-002.2 edge 86 the base domain is the host part of TSOLVE_SITE_ORIGIN_TEMPLATE after {slug}.")
    void readsTheBaseDomainFromTheTemplate_AC_002_2(String template, String baseDomain, List<String> noSiteHosts) {
        if (baseDomain == null) {
            assertThatThrownBy(() -> HostClassifier.fromTemplate(template))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining(VARIABLE)
                    .satisfies(failure -> {
                        if (template != null && !template.isBlank()) {
                            assertThat(failure.getMessage()).doesNotContain(template);
                        }
                    });
            return;
        }
        HostClassifier classifier = HostClassifier.fromTemplate(template);

        assertThat(classifier.baseDomain()).isEqualTo(baseDomain);
        assertThat(noSiteHosts)
                .allSatisfy(host -> assertThat(classifier.classify(host)).isEqualTo(NOT_A_SITE));
    }
}
