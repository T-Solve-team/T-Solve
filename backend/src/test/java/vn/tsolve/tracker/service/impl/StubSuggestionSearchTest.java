package vn.tsolve.tracker.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import vn.tsolve.tracker.dto.IssueSuggestion;

class StubSuggestionSearchTest {

    private static final UUID PROJECT = UUID.fromString("5b0e7a1c-3f2d-4e8b-9c11-2a7d4f6e8b01");

    @Test
    void suggestsNothingWhenNotConfigured_AC_011_5() {
        assertThat(new StubSuggestionSearch("").suggest(PROJECT, "VPN drops", ""))
                .isEmpty();
        assertThat(new StubSuggestionSearch(null).suggest(PROJECT, "VPN drops", ""))
                .isEmpty();
    }

    @Test
    void failsWhenConfiguredUnavailable_AC_011_6() {
        StubSuggestionSearch search = new StubSuggestionSearch("unavailable");

        assertThatThrownBy(() -> search.suggest(PROJECT, "VPN drops", "")).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void suggestsTheConfiguredTextWithNoSources_AC_011_4() {
        assertThat(new StubSuggestionSearch("Restart the VPN service.").suggest(PROJECT, "VPN drops", ""))
                .contains(new IssueSuggestion(java.util.List.of(), "Restart the VPN service."));
    }
}
