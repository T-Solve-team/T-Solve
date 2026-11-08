package vn.tsolve.chatbot.answer.service.impl;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.RecordComponent;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import vn.tsolve.chatbot.answer.dto.ChatbotCitation;
import vn.tsolve.chatbot.answer.dto.CitedSolution;
import vn.tsolve.chatbot.answer.dto.TicketSolutionCitation;
import vn.tsolve.chatbot.answer.dto.WikiDocumentCitation;
import vn.tsolve.chatbot.source.service.WikiSources.WikiPassage;
import vn.tsolve.search.retrieval.dto.RetrievedSolution;

class CitationsTest {
    private static final UUID DOCUMENT = UUID.fromString("80000000-0000-0000-0000-000000000001");
    private final List<ChatbotSource> kept = List.of(
            ChatbotSource.of(solution("first", "workaround")).numbered(1),
            ChatbotSource.of(new WikiPassage(DOCUMENT, "Policy", "passage", 0.8))
                    .numbered(2),
            ChatbotSource.of(solution("third", "workaround")).numbered(3));

    @Test
    void listsOnlyCitedSourcesInOrder_AC_006_1() {
        var citations = Citations.of("See [3], then [1] and [3] again.", kept);

        assertThat(citations.text()).isEqualTo("See [3], then [1] and [3] again.");
        assertThat(citations.citations()).extracting(ChatbotCitation::number).containsExactly(1, 3);
        assertThat(((TicketSolutionCitation) citations.citations().get(1))
                        .solution()
                        .symptoms())
                .isEqualTo("third");
    }

    @Test
    void listsAdjacentMarkersAndIgnoresOtherForms_AC_006_1() {
        var citations = Citations.of("Both [1][2]; not [01], [ 3 ], [0] or [1, 3].", kept);

        assertThat(citations.citations()).extracting(ChatbotCitation::number).containsExactly(1, 2);
        assertThat(citations.text()).isEqualTo("Both [1][2]; not [01], [ 3 ], [0] or [1, 3].");
    }

    @ParameterizedTest
    @CsvSource(
            delimiter = '|',
            value = {
                "Restart [9] the service [1].|Restart the service [1].",
                "Restart the service [1] [42].|Restart the service [1].",
                "Restart [4][1] now.|Restart [1] now.",
                "Restart [99]|Restart",
                "Keep [1, 9] as text [1].|Keep [1, 9] as text [1]."
            })
    void removesUnknownMarkers_AC_006_2(String text, String expected) {
        var citations = Citations.of(text, kept);

        assertThat(citations.text()).isEqualTo(expected);
    }

    @Test
    void citesNothingWhenOnlyUnknownMarkers_AC_006_2() {
        var citations = Citations.of("Unknown [9] only.", kept);

        assertThat(citations.citations()).isEmpty();
        assertThat(citations.text()).isEqualTo("Unknown only.");
    }

    @Test
    void mapsTicketSourceToFiveSections_AC_006_3() {
        var citation = (TicketSolutionCitation)
                ChatbotSource.of(solution("symptoms", "  ")).numbered(1).citation();

        assertThat(citation.number()).isEqualTo(1);
        assertThat(citation.solution()).isEqualTo(new CitedSolution("symptoms", "cause", "steps", null, "scope"));
        assertThat(TicketSolutionCitation.class.getRecordComponents())
                .extracting(RecordComponent::getName)
                .containsExactly("number", "solution");
        assertThat(CitedSolution.class.getRecordComponents())
                .extracting(RecordComponent::getName)
                .containsExactly("symptoms", "rootCause", "resolutionSteps", "workaround", "scopeOfApplicability");
    }

    @Test
    void mapsWikiSourceToDocument_AC_006_4() {
        var citation = (WikiDocumentCitation)
                Citations.of("Policy [2]", kept).citations().getFirst();

        assertThat(citation).isEqualTo(new WikiDocumentCitation(2, DOCUMENT, "Policy"));
    }

    private static RetrievedSolution solution(String symptoms, String workaround) {
        return new RetrievedSolution(symptoms, "cause", "steps", workaround, "scope", 0.9);
    }
}
