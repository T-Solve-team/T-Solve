package vn.tsolve.triage.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AssignableTypeFilter;
import vn.tsolve.ai.client.ChatMessage;
import vn.tsolve.ai.client.EmbeddingClient;
import vn.tsolve.ai.client.LlmClient;
import vn.tsolve.ai.exception.AiUnavailableException;
import vn.tsolve.common.config.TriageProperties;
import vn.tsolve.search.retrieval.dto.RetrievedTicketSolution;
import vn.tsolve.search.retrieval.service.SolutionRetrieval;
import vn.tsolve.tracker.dto.IssueSuggestion;
import vn.tsolve.tracker.dto.SuggestionSource;
import vn.tsolve.tracker.service.SuggestionSearch;
import vn.tsolve.triage.dto.WikiSource;
import vn.tsolve.triage.service.WikiSourceRetrieval;

class SuggestionGeneratorTest {
    private static final UUID PROJECT = UUID.randomUUID();
    private static final float[] VECTOR = new float[EmbeddingClient.DIMENSIONS];

    private final EmbeddingClient embeddings = mock(EmbeddingClient.class);
    private final LlmClient llm = mock(LlmClient.class);
    private final SolutionRetrieval tickets = mock(SolutionRetrieval.class);
    private final WikiSourceRetrieval wiki = mock(WikiSourceRetrieval.class);
    private final SuggestionSearch generator =
            new SuggestionGenerator(embeddings, llm, tickets, wiki, new TriageProperties(0.7));

    @BeforeEach
    void defaults() {
        when(embeddings.embed(anyList())).thenReturn(List.of(VECTOR));
        when(tickets.nearestInProjectDepartment(any(), any(), anyDouble(), anyInt()))
                .thenReturn(List.of());
        when(wiki.nearest(any(), anyDouble(), anyInt())).thenReturn(List.of());
        when(llm.chat(anyList(), any())).thenReturn("Restart the VPN client.");
    }

    @Test
    void embedsSummaryBlankLineAndDescriptionCutTo8000_AC_005_3() {
        generator.suggest(PROJECT, "VPN drops", "Every 10 minutes");
        verify(embeddings).embed(List.of("VPN drops\n\nEvery 10 minutes"));

        generator.suggest(PROJECT, "VPN drops", "  ");
        generator.suggest(PROJECT, "VPN drops", null);
        verify(embeddings, org.mockito.Mockito.times(2)).embed(List.of("VPN drops"));

        String longDescription = "x".repeat(9000);
        generator.suggest(PROJECT, "VPN drops", longDescription);
        verify(embeddings).embed(List.of(("VPN drops\n\n" + longDescription).substring(0, 8000)));
    }

    @Test
    void keepsAtMostFiveOfBothAtTheThresholdMostSimilarFirst_AC_005_4() {
        RetrievedTicketSolution t90 = ticket(0.9);
        RetrievedTicketSolution t80 = ticket(0.8);
        RetrievedTicketSolution t75 = ticket(0.75);
        RetrievedTicketSolution t70 = ticket(0.7);
        when(tickets.nearestInProjectDepartment(PROJECT, VECTOR, 0.7, 5)).thenReturn(List.of(t90, t80, t75, t70));
        WikiSource w85 = document(0.85);
        WikiSource w80 = document(0.8);
        WikiSource w72 = document(0.72);
        when(wiki.nearest(VECTOR, 0.7, 5)).thenReturn(List.of(w85, w80, w72));

        IssueSuggestion suggestion =
                generator.suggest(PROJECT, "VPN drops", null).orElseThrow();

        // Equal similarity (0.8): the ticket comes before the Wiki document.
        assertThat(suggestion.sources())
                .extracting(SuggestionSource::id)
                .containsExactly(
                        t90.ticketId().toString(),
                        w85.documentId(),
                        t80.ticketId().toString(),
                        w80.documentId(),
                        t75.ticketId().toString());
    }

    @Test
    void returnsTheKeptSourcesAndTheChatAnswer_AC_005_5() {
        RetrievedTicketSolution solution = ticket(0.9);
        when(tickets.nearestInProjectDepartment(any(), any(), anyDouble(), anyInt()))
                .thenReturn(List.of(solution));

        IssueSuggestion suggestion =
                generator.suggest(PROJECT, "VPN drops", "Every 10 minutes").orElseThrow();

        assertThat(suggestion.suggestedSolution()).isEqualTo("Restart the VPN client.");
        assertThat(suggestion.sources()).hasSize(1);
        String prompt = userPrompt();
        assertThat(prompt).contains("VPN drops\n\nEvery 10 minutes");
        assertThat(prompt)
                .contains(
                        solution.symptoms(),
                        solution.rootCause(),
                        solution.resolutionSteps(),
                        solution.workaround(),
                        solution.scopeOfApplicability());
    }

    @Test
    void storesIdsButGivesTheModelNoTicketIdAndWikiByTitle_AC_005_6() {
        RetrievedTicketSolution solution = ticket(0.9);
        WikiSource document = document(0.8);
        when(tickets.nearestInProjectDepartment(any(), any(), anyDouble(), anyInt()))
                .thenReturn(List.of(solution));
        when(wiki.nearest(any(), anyDouble(), anyInt())).thenReturn(List.of(document));

        IssueSuggestion suggestion =
                generator.suggest(PROJECT, "VPN drops", null).orElseThrow();

        assertThat(suggestion.sources())
                .containsExactly(
                        new SuggestionSource("TICKET", solution.ticketId().toString(), null, 0.9),
                        new SuggestionSource("WIKI", document.documentId(), document.title(), 0.8));
        String prompt = userPrompt();
        assertThat(prompt).doesNotContain(solution.ticketId().toString(), document.documentId());
        assertThat(prompt).contains("Solution 1", document.title(), document.content());
    }

    @Test
    void capsWikiContentAt2000InThePrompt_AC_005_6() {
        WikiSource document = new WikiSource("doc-1", "VPN guide", "w".repeat(2500), 0.8);
        when(wiki.nearest(any(), anyDouble(), anyInt())).thenReturn(List.of(document));

        generator.suggest(PROJECT, "VPN drops", null);

        assertThat(userPrompt()).contains("w".repeat(2000)).doesNotContain("w".repeat(2001));
    }

    @Test
    void callsNoChatModelWithoutSources_AC_005_7() {
        assertThat(generator.suggest(PROJECT, "VPN drops", "Every 10 minutes")).isEmpty();
        verify(llm, never()).chat(anyList(), any());
    }

    @Test
    void failsWithoutTextWhenAModelFailsOrAnswersBlank_AC_005_8() {
        when(tickets.nearestInProjectDepartment(any(), any(), anyDouble(), anyInt()))
                .thenReturn(List.of(ticket(0.9)));

        when(llm.chat(anyList(), any())).thenReturn("  ");
        assertThatThrownBy(() -> generator.suggest(PROJECT, "VPN drops secret", null))
                .isInstanceOf(IllegalStateException.class)
                .message()
                .doesNotContain("VPN", "secret", "Symptoms");

        when(llm.chat(anyList(), any())).thenThrow(new AiUnavailableException());
        assertThatThrownBy(() -> generator.suggest(PROJECT, "VPN drops", null))
                .isInstanceOf(AiUnavailableException.class);

        when(embeddings.embed(anyList())).thenThrow(new AiUnavailableException());
        assertThatThrownBy(() -> generator.suggest(PROJECT, "VPN drops", null))
                .isInstanceOf(AiUnavailableException.class);
    }

    @Test
    void standInWikiReturnsNoDocument_AC_005_12() {
        assertThat(new NoWikiSources().nearest(VECTOR, 0, 5)).isEmpty();
    }

    @Test
    void callsNoModelForABlankSummary_AC_005_13() {
        for (String summary : new String[] {null, "", "   "}) {
            assertThat(generator.suggest(PROJECT, summary, "Every 10 minutes")).isEmpty();
        }
        verifyNoInteractions(embeddings, llm, tickets, wiki);
    }

    @Test
    void isTheOnlySuggestionSearch_AC_005_14() {
        var scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AssignableTypeFilter(SuggestionSearch.class));
        assertThat(scanner.findCandidateComponents("vn.tsolve"))
                .extracting(bean -> bean.getBeanClassName())
                .containsExactly(SuggestionGenerator.class.getName());
    }

    private String userPrompt() {
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ChatMessage>> messages = ArgumentCaptor.forClass(List.class);
        verify(llm).chat(messages.capture(), eq(null));
        assertThat(messages.getValue())
                .extracting(ChatMessage::role)
                .containsExactly(ChatMessage.Role.SYSTEM, ChatMessage.Role.USER);
        return messages.getValue().get(1).content();
    }

    private static RetrievedTicketSolution ticket(double similarity) {
        int n = (int) Math.round(similarity * 100);
        return new RetrievedTicketSolution(
                UUID.randomUUID(),
                "Symptoms " + n,
                "Root cause " + n,
                "Steps " + n,
                "Workaround " + n,
                "Scope " + n,
                similarity);
    }

    private static WikiSource document(double similarity) {
        int n = (int) Math.round(similarity * 100);
        return new WikiSource("doc-" + n, "Wiki title " + n, "Wiki content " + n, similarity);
    }

    static {
        IntStream.range(0, VECTOR.length).forEach(i -> VECTOR[i] = i == 0 ? 1 : 0);
    }
}
