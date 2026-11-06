package vn.tsolve.tracker.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import vn.tsolve.tracker.dto.IssueSuggestion;

class StubSuggestionSearchTest {

    @Test
    void suggestsNothingWhenNotConfigured_AC_011_5() {
        assertThat(new StubSuggestionSearch("").suggest(4L, "VPN drops", "")).isEmpty();
        assertThat(new StubSuggestionSearch(null).suggest(4L, "VPN drops", "")).isEmpty();
    }

    @Test
    void failsWhenConfiguredUnavailable_AC_011_6() {
        StubSuggestionSearch search = new StubSuggestionSearch("unavailable");

        assertThatThrownBy(() -> search.suggest(4L, "VPN drops", "")).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void suggestsTheConfiguredTextWithNoSources_AC_011_4() {
        assertThat(new StubSuggestionSearch("Restart the VPN service.").suggest(4L, "VPN drops", ""))
                .contains(new IssueSuggestion(java.util.List.of(), "Restart the VPN service."));
    }
}
