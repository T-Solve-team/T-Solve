package vn.tsolve.tracker.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.anyLong;
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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import vn.tsolve.tracker.dto.IssueSuggestion;
import vn.tsolve.tracker.dto.SuggestionJobRequest;
import vn.tsolve.tracker.dto.SuggestionSource;
import vn.tsolve.tracker.entity.SuggestionResult;
import vn.tsolve.tracker.repository.SuggestionResultRepository;
import vn.tsolve.tracker.service.SuggestionSearch;

@ExtendWith({MockitoExtension.class, OutputCaptureExtension.class})
class SuggestionJobServiceImplTest {

    private static final SuggestionJobRequest REQUEST =
            new SuggestionJobRequest("ITSUP-1", 4L, "VPN drops every hour", "Reconnect fails after sleep.");

    @Mock
    private SuggestionResultRepository repository;

    @Mock
    private SuggestionSearch search;

    @Test
    void asksSearchWithTheJobsProjectSummaryAndDescription_AC_011_3() {
        given(repository.findByJiraIssueKey("ITSUP-1")).willReturn(Optional.of(newResult()));
        given(search.suggest(4L, "VPN drops every hour", "Reconnect fails after sleep."))
                .willReturn(Optional.empty());

        service().run(REQUEST);

        verify(search).suggest(4L, "VPN drops every hour", "Reconnect fails after sleep.");
    }

    @Test
    void storesTheSourcesAndTheSuggestedSolution_AC_011_4() {
        given(repository.findByJiraIssueKey("ITSUP-1")).willReturn(Optional.of(newResult()));
        given(search.suggest(anyLong(), anyString(), anyString()))
                .willReturn(Optional.of(new IssueSuggestion(
                        List.of(
                                new SuggestionSource("TICKET", "ITSUP-7", "VPN reconnect loop", 0.91),
                                new SuggestionSource("WIKI", "42", "VPN client setup", 0.83)),
                        "Update the VPN client to 5.2 and turn off Wi-Fi power saving.")));
        given(repository.fillSuggestion(anyString(), anyString(), anyString())).willReturn(1);

        service().run(REQUEST);

        verify(repository)
                .fillSuggestion(
                        "ITSUP-1",
                        "[{\"type\":\"TICKET\",\"id\":\"ITSUP-7\",\"title\":\"VPN reconnect loop\",\"similarity\":0.91},"
                                + "{\"type\":\"WIKI\",\"id\":\"42\",\"title\":\"VPN client setup\",\"similarity\":0.83}]",
                        "Update the VPN client to 5.2 and turn off Wi-Fi power saving.");
    }

    @Test
    void storesNothingWhenSearchHasNoSuggestion_AC_011_5() {
        given(repository.findByJiraIssueKey("ITSUP-1")).willReturn(Optional.of(newResult()));
        given(search.suggest(anyLong(), anyString(), anyString())).willReturn(Optional.empty());

        service().run(REQUEST);

        verify(repository, never()).fillSuggestion(anyString(), anyString(), anyString());
    }

    @Test
    void storesNothingWhenTheSuggestedSolutionIsBlank_AC_011_5() {
        given(repository.findByJiraIssueKey("ITSUP-1")).willReturn(Optional.of(newResult()));
        given(search.suggest(anyLong(), anyString(), anyString()))
                .willReturn(Optional.of(new IssueSuggestion(List.of(), "   ")));

        service().run(REQUEST);

        verify(repository, never()).fillSuggestion(anyString(), anyString(), anyString());
    }

    @Test
    void leavesTheResultUnchangedAndLogsOnlyKeyAndErrorTypeWhenSearchFails_AC_011_6(CapturedOutput output) {
        given(repository.findByJiraIssueKey("ITSUP-1")).willReturn(Optional.of(newResult()));
        given(search.suggest(anyLong(), anyString(), anyString()))
                .willThrow(new IllegalStateException("model said: VPN drops every hour"));

        assertThatCode(() -> service().run(REQUEST)).doesNotThrowAnyException();

        verify(repository, never()).fillSuggestion(anyString(), anyString(), anyString());
        assertThat(output).contains("ITSUP-1").contains("IllegalStateException");
        assertThat(output).doesNotContain("VPN drops").doesNotContain("Reconnect fails");
    }

    @Test
    void neverThrowsAndLogsOnlyKeyAndErrorTypeWhenTheResultCannotBeRead_AC_011_6(CapturedOutput output) {
        given(repository.findByJiraIssueKey("ITSUP-1"))
                .willThrow(new DataAccessResourceFailureException("connection lost while reading VPN drops"));

        assertThatCode(() -> service().run(REQUEST)).doesNotThrowAnyException();

        verifyNoInteractions(search);
        assertThat(output).contains("ITSUP-1").contains("DataAccessResourceFailureException");
        assertThat(output).doesNotContain("VPN drops");
    }

    @Test
    void neverThrowsAndLogsOnlyKeyAndErrorTypeWhenTheSuggestionCannotBeStored_AC_011_6(CapturedOutput output) {
        given(repository.findByJiraIssueKey("ITSUP-1")).willReturn(Optional.of(newResult()));
        given(search.suggest(anyLong(), anyString(), anyString()))
                .willReturn(Optional.of(new IssueSuggestion(List.of(), "Restart the VPN service.")));
        given(repository.fillSuggestion(anyString(), anyString(), anyString()))
                .willThrow(new DataAccessResourceFailureException("could not store: Restart the VPN service."));

        assertThatCode(() -> service().run(REQUEST)).doesNotThrowAnyException();

        assertThat(output).contains("ITSUP-1").contains("DataAccessResourceFailureException");
        assertThat(output).doesNotContain("Restart the VPN service");
    }

    @Test
    void changesNothingWhenTheResultNoLongerExists_AC_011_7() {
        given(repository.findByJiraIssueKey("ITSUP-1")).willReturn(Optional.empty());

        service().run(REQUEST);

        verifyNoInteractions(search);
        verify(repository, never()).fillSuggestion(anyString(), anyString(), anyString());
    }

    @Test
    void changesNothingWhenTheResultAlreadyHasASuggestion_AC_011_7() {
        SuggestionResult filled = mock(SuggestionResult.class);
        given(filled.hasSuggestion()).willReturn(true);
        given(repository.findByJiraIssueKey("ITSUP-1")).willReturn(Optional.of(filled));

        service().run(REQUEST);

        verifyNoInteractions(search);
        verify(repository, never()).fillSuggestion(anyString(), anyString(), anyString());
    }

    private SuggestionJobServiceImpl service() {
        return new SuggestionJobServiceImpl(repository, search, new ObjectMapper());
    }

    private static SuggestionResult newResult() {
        return new SuggestionResult(4L, "ITSUP-1", Instant.parse("2026-11-07T01:00:00Z"));
    }
}
