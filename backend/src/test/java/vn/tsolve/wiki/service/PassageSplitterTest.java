package vn.tsolve.wiki.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class PassageSplitterTest {
    private final PassageSplitter splitter = new PassageSplitter();

    @Test
    void keepsShortTextAndIgnoresBlankText_AC_004_14() {
        assertThat(splitter.split("  A short guide.  ")).containsExactly("A short guide.");
        assertThat(splitter.split(" \t\r\n \n")).isEmpty();
        assertThat(splitter.split("")).isEmpty();
    }

    @Test
    void prefersParagraphBreaksWithoutOverlap_AC_004_14() {
        String first = "a".repeat(600);
        String second = "b".repeat(600);
        assertThat(splitter.split(first + "\n\n" + second)).containsExactly(first, second);
        assertThat(splitter.split(first + "\r\n\r\n" + second)).containsExactly(first, second);
        assertThat(splitter.split(first + "\n \t\n" + second)).containsExactly(first, second);
    }

    @Test
    void cutsAtWhitespaceAndKeepsAllWordsInOrder_AC_004_14() {
        String content = "guide ".repeat(400).strip();
        List<String> passages = splitter.split(content);
        assertThat(passages).hasSize(3).allSatisfy(passage -> {
            assertThat(passage.length()).isBetween(1, 1000);
            assertThat(passage).doesNotStartWith(" ").doesNotEndWith(" ");
        });
        assertThat(String.join(" ", passages)).isEqualTo(content);
    }

    @Test
    void hardCutsLongTokensAndKeepsTheExactBoundary_AC_004_14() {
        assertThat(splitter.split("a".repeat(1000))).containsExactly("a".repeat(1000));
        assertThat(splitter.split("a".repeat(1001))).containsExactly("a".repeat(1000), "a");
        assertThat(splitter.split("a".repeat(1000) + " " + "b".repeat(1000)))
                .containsExactly("a".repeat(1000), "b".repeat(1000));
    }

    @Test
    void keepsOnlyTheFirstTwoHundredPassages_AC_004_14() {
        var passages = splitter.split("a".repeat(200_000) + "discarded");
        assertThat(passages).hasSize(200).allSatisfy(passage -> assertThat(passage)
                .hasSize(1000));
        assertThat(String.join("", passages)).isEqualTo("a".repeat(200_000));
    }
}
