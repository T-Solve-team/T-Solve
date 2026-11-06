package vn.tsolve.search.query.service.impl;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ExcerptsTest {
    @Test
    void collapsesSpaceAndCutsAt200_AC_007_4() {
        assertThat(Excerpts.of("  VPN\n\t fails ", " Restart   router ")).isEqualTo("VPN fails Restart router");
        assertThat(Excerpts.of(null, "  restart ")).isEqualTo("restart");
        assertThat(Excerpts.of(null, null)).isEmpty();
        assertThat(Excerpts.of("x".repeat(199), "resolution")).isEqualTo("x".repeat(199) + " ");
        assertThat(Excerpts.of("x".repeat(250), null)).hasSize(200);
    }
}
