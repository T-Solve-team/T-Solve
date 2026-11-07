package vn.tsolve.tracker.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import vn.tsolve.tracker.dto.IssueSuggestion;
import vn.tsolve.tracker.dto.SuggestionSource;
import vn.tsolve.tracker.entity.SuggestionResult;
import vn.tsolve.tracker.exception.SuggestionJobFailedException;
import vn.tsolve.tracker.repository.SuggestionResultRepository;
import vn.tsolve.tracker.service.SuggestionSearch;

@ExtendWith({MockitoExtension.class, OutputCaptureExtension.class})
class SuggestionJobServiceImplTest {

    private static final UUID PROJECT = UUID.fromString("5b0e7a1c-3f2d-4e8b-9c11-2a7d4f6e8b01");
    private static final UUID SUGGESTION_ID = UUID.fromString("0c9f3e52-6a41-4d7b-8e2f-91b3c5d7a640");
    private static final SuggestionResult RESULT = new SuggestionResult(
            PROJECT,
            "ITSUP-1",
            "VPN drops every hour",
            "Reconnect fails after sleep.",
            Instant.parse("2026-11-07T01:00:00Z"));

    @Mock
    private SuggestionResultRepository repository;

    @Mock
    private SuggestionSearch search;

    @Test
    void asksSearchWithTheProjectAndTheIssueTextStoredInTheRow_AC_011_3() {
        given(repository.findById(SUGGESTION_ID)).willReturn(Optional.of(RESULT));
        given(search.suggest(PROJECT, "VPN drops every hour", "Reconnect fails after sleep."))
                .willReturn(Optional.empty());

        service().run(SUGGESTION_ID);

        verify(search).suggest(PROJECT, "VPN drops every hour", "Reconnect fails after sleep.");
    }

    @Test
    void asksSearchWithEmptyTextForARowRecordedBeforeTheTextWasStored_AC_011_3() {
        UUID olderId = UUID.fromString("7e21a4b8-0d5c-4f39-a6e1-c48b2d9f0e17");
        SuggestionResult older =
                new SuggestionResult(PROJECT, "ITSUP-2", null, null, Instant.parse("2026-11-01T01:00:00Z"));
        given(repository.findById(olderId)).willReturn(Optional.of(older));
        given(search.suggest(PROJECT, "", "")).willReturn(Optional.empty());

        service().run(olderId);

        verify(search).suggest(PROJECT, "", "");
    }

    @Test
    void storesTheSourcesAndTheSuggestedSolution_AC_011_4() {
        given(repository.findById(SUGGESTION_ID)).willReturn(Optional.of(RESULT));
        given(search.suggest(any(UUID.class), anyString(), anyString()))
                .willReturn(Optional.of(new IssueSuggestion(
                        List.of(
                                new SuggestionSource("TICKET", "ITSUP-7", "VPN reconnect loop", 0.91),
                                new SuggestionSource("WIKI", "42", "VPN client setup", 0.83)),
                        "Update the VPN client to 5.2 and turn off Wi-Fi power saving.")));
        given(repository.fillSuggestion(anyString(), anyString(), anyString())).willReturn(1);

        service().run(SUGGESTION_ID);

        verify(repository)
                .fillSuggestion(
                        "ITSUP-1",
                        "[{\"type\":\"TICKET\",\"id\":\"ITSUP-7\",\"title\":\"VPN reconnect loop\",\"similarity\":0.91},"
                                + "{\"type\":\"WIKI\",\"id\":\"42\",\"title\":\"VPN client setup\",\"similarity\":0.83}]",
                        "Update the VPN client to 5.2 and turn off Wi-Fi power saving.");
    }

    @Test
    void storesNothingWhenSearchHasNoSuggestion_AC_011_5() {
        given(repository.findById(SUGGESTION_ID)).willReturn(Optional.of(RESULT));
        given(search.suggest(any(UUID.class), anyString(), anyString())).willReturn(Optional.empty());

        service().run(SUGGESTION_ID);

        verify(repository, never()).fillSuggestion(anyString(), anyString(), anyString());
    }

    @Test
    void storesNothingWhenTheSuggestedSolutionIsBlank_AC_011_5() {
        given(repository.findById(SUGGESTION_ID)).willReturn(Optional.of(RESULT));
        given(search.suggest(any(UUID.class), anyString(), anyString()))
                .willReturn(Optional.of(new IssueSuggestion(List.of(), "   ")));

        service().run(SUGGESTION_ID);

        verify(repository, never()).fillSuggestion(anyString(), anyString(), anyString());
    }

    @Test
    void failsForARetryWithOnlyKeyAndErrorTypeWhenSearchFails_AC_011_6(CapturedOutput output) {
        given(repository.findById(SUGGESTION_ID)).willReturn(Optional.of(RESULT));
        given(search.suggest(any(UUID.class), anyString(), anyString()))
                .willThrow(new IllegalStateException("model said: VPN drops every hour"));

        assertThatThrownBy(() -> service().run(SUGGESTION_ID))
                .isInstanceOf(SuggestionJobFailedException.class)
                .hasNoCause()
                .hasMessageContaining("ITSUP-1")
                .hasMessageContaining("IllegalStateException")
                .hasMessageNotContaining("VPN drops");
        verify(repository, never()).fillSuggestion(anyString(), anyString(), anyString());
        assertThat(output).contains("ITSUP-1").doesNotContain("VPN drops").doesNotContain("Reconnect fails");
    }

    @Test
    void failsForARetryWhenTheResultCannotBeRead_AC_011_6() {
        given(repository.findById(SUGGESTION_ID))
                .willThrow(new DataAccessResourceFailureException("connection lost while reading VPN drops"));

        assertThatThrownBy(() -> service().run(SUGGESTION_ID))
                .isInstanceOf(SuggestionJobFailedException.class)
                .hasNoCause()
                .hasMessageContaining("DataAccessResourceFailureException")
                .hasMessageNotContaining("VPN drops");
        verifyNoInteractions(search);
    }

    @Test
    void failsForARetryWhenTheSuggestionCannotBeStored_AC_011_6() {
        given(repository.findById(SUGGESTION_ID)).willReturn(Optional.of(RESULT));
        given(search.suggest(any(UUID.class), anyString(), anyString()))
                .willReturn(Optional.of(new IssueSuggestion(List.of(), "Restart the VPN service.")));
        given(repository.fillSuggestion(anyString(), anyString(), anyString()))
                .willThrow(new DataAccessResourceFailureException("could not store: Restart the VPN service."));

        assertThatThrownBy(() -> service().run(SUGGESTION_ID))
                .isInstanceOf(SuggestionJobFailedException.class)
                .hasNoCause()
                .hasMessageContaining("ITSUP-1")
                .hasMessageNotContaining("Restart the VPN service");
    }

    @Test
    void changesNothingWhenTheResultNoLongerExists_AC_011_7() {
        given(repository.findById(SUGGESTION_ID)).willReturn(Optional.empty());

        service().run(SUGGESTION_ID);

        verifyNoInteractions(search);
        verify(repository, never()).fillSuggestion(anyString(), anyString(), anyString());
    }

    @Test
    void changesNothingWhenTheResultAlreadyHasASuggestion_AC_011_7() {
        SuggestionResult filled = mock(SuggestionResult.class);
        given(filled.hasSuggestion()).willReturn(true);
        given(repository.findById(SUGGESTION_ID)).willReturn(Optional.of(filled));

        service().run(SUGGESTION_ID);

        verifyNoInteractions(search);
        verify(repository, never()).fillSuggestion(anyString(), anyString(), anyString());
    }

    private SuggestionJobServiceImpl service() {
        return new SuggestionJobServiceImpl(repository, search, new ObjectMapper());
    }
}
