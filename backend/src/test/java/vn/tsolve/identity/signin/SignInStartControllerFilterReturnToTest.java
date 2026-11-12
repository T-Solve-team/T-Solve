package vn.tsolve.identity.signin;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/** The returnTo filter rules of AC-001.3: an open-redirect and header-injection guard. */
class SignInStartControllerFilterReturnToTest {

    static Stream<String> keptReturnTo() {
        return Stream.of("/", "/tickets/42", "/search?q=a%20b#top", "/" + "a".repeat(2047));
    }

    @ParameterizedTest(name = "[{index}]")
    @MethodSource("keptReturnTo")
    @DisplayName("AC-001.3 keeps a same-site relative returnTo")
    void keepsSameSiteRelativeReturnTo_AC_001_3(String returnTo) {
        assertThat(SignInStartController.filterReturnTo(returnTo)).isEqualTo(returnTo);
    }

    static Stream<Arguments> replacedReturnTo() {
        return Stream.of(
                Arguments.of("null", null),
                Arguments.of("empty", ""),
                Arguments.of("no leading slash", "tickets"),
                Arguments.of("protocol-relative", "//evil.example"),
                Arguments.of("triple slash", "///evil.example"),
                Arguments.of("slash backslash", "/\\evil"),
                Arguments.of("double backslash", "\\\\evil"),
                Arguments.of("backslash inside", "/a\\b"),
                Arguments.of("absolute URL", "https://evil.example"),
                Arguments.of("javascript scheme", "javascript:alert(1)"),
                Arguments.of("tab", "/a\tb"),
                Arguments.of("leading space", " /a"),
                Arguments.of("space", "/a b"),
                Arguments.of("line feed", "/a\nb"),
                Arguments.of("carriage return", "/a\rb"),
                Arguments.of("NUL", "/a\u0000b"),
                Arguments.of("DEL", "/a\u007Fb"),
                Arguments.of("2,049 characters", "/" + "a".repeat(2048)),
                Arguments.of("3,000 characters", "/" + "a".repeat(2999)));
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @MethodSource("replacedReturnTo")
    @DisplayName("AC-001.3 replaces any other returnTo with /")
    void replacesAnyOtherReturnToWithRoot_AC_001_3_edge19(String label, String returnTo) {
        assertThat(SignInStartController.filterReturnTo(returnTo)).isEqualTo("/");
    }
}
