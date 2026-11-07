package vn.tsolve.search.query.service.impl;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ExcerptsTest {
    @Test
    void cutsAt200CodePointsWithoutSplittingEmoji_BUG_141() {
        String prefix = "x".repeat(199) + "\uD83D\uDE00";
        String excerpt = Excerpts.of(prefix + "more", null);
        assertThat(excerpt).isEqualTo(prefix);
        assertThat(excerpt.codePointCount(0, excerpt.length())).isEqualTo(200);
        assertThat(Character.isLowSurrogate(excerpt.charAt(excerpt.length() - 1)))
                .isTrue();
        assertThat(Excerpts.of("\uD83D\uDE00".repeat(201), null)).isEqualTo("\uD83D\uDE00".repeat(200));
    }

    @Test
    void collapsesSpaceAndCutsAt200_AC_007_4() {
        assertThat(Excerpts.of("  VPN\n\t fails ", " Restart   router ")).isEqualTo("VPN fails Restart router");
        assertThat(Excerpts.of(null, "  restart ")).isEqualTo("restart");
        assertThat(Excerpts.of(null, null)).isEmpty();
        assertThat(Excerpts.of("x".repeat(199), "resolution")).isEqualTo("x".repeat(199) + " ");
        assertThat(Excerpts.of("x".repeat(250), null)).hasSize(200);
    }
}
