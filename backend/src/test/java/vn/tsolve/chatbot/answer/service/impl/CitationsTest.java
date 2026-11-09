package vn.tsolve.chatbot.answer.service.impl;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import vn.tsolve.chatbot.answer.dto.ChatbotCitation;
import vn.tsolve.chatbot.answer.dto.CitedSolution;
import vn.tsolve.chatbot.answer.dto.TicketSolutionCitation;
import vn.tsolve.chatbot.answer.dto.WikiDocumentCitation;
import vn.tsolve.chatbot.source.service.WikiSources.WikiPassage;
import vn.tsolve.search.retrieval.dto.RetrievedSolution;

class CitationsTest {
    @Test
    void listsOnlyCitedSourcesInOrder_AC_006_1() {
        var source = ChatbotSource.of(new WikiPassage(UUID.randomUUID(), "Policy", "passage", 0.9));
        var kept = List.of(source.numbered(1), source.numbered(2), source.numbered(3), source.numbered(4));

        var result = Citations.parse("Third [3], first [1], third again [3]", kept);

        assertThat(result.text()).isEqualTo("Third [3], first [1], third again [3]");
        assertThat(result.citations())
                .containsExactly(kept.get(0).citation(), kept.get(2).citation());
        assertThat(result.citations()).extracting(ChatbotCitation::number).containsExactly(1, 3);
        assertThat(Citations.parse("Adjacent [1][2]", kept).citations())
                .containsExactly(kept.get(0).citation(), kept.get(1).citation());
        assertThat(Citations.parse("No markers", kept).citations()).isEmpty();
    }

    @ParameterizedTest
    @CsvSource(
            delimiter = '|',
            value = {
                "'foo [9] bar [1]'|'foo bar [1]'|true",
                "'[9][1][99]'|'[1]'|true",
                "'[0] [01] [1, 2] [ 1 ] [100] [1]'|'[0] [01] [1, 2] [ 1 ] [100] [1]'|true",
                "'  foo  [1]  bar  '|'  foo  [1]  bar  '|true",
                "'  foo [9] bar [1]\nnext  line  '|'  foo bar [1]\nnext  line  '|true",
                "'foo\n[9]\nbar [1]'|'foo\n\nbar [1]'|true",
                "'foo\t[9]\tbar [1]'|'foo\t\tbar [1]'|true",
                "'foo [9] [99] bar [1]'|'foo bar [1]'|true",
                "'foo[9]bar [1]'|'foobar [1]'|true",
                "'[9] [99]'|' '|false",
                "'[9]'|''|false",
                "'[[9]9]'|''|false",
                "'x [[9]9] [1]'|'x [1]'|true",
                "'[0] [01] [1, 2] [ 1 ] [100]'|'[0] [01] [1, 2] [ 1 ] [100]'|false",
                "''|''|false"
            })
    void removesUnknownMarkers_AC_006_2(String text, String expected, boolean cited) {
        var source = ChatbotSource.of(new WikiPassage(UUID.randomUUID(), "Policy", "passage", 0.9))
                .numbered(1);

        var result = Citations.parse(text, List.of(source));

        assertThat(result.text()).isEqualTo(expected);
        assertThat(result.citations()).isEqualTo(cited ? List.of(source.citation()) : List.of());
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "  \t\n", "Temporary bypass"})
    void mapsTicketSourceToFiveSections_AC_006_3(String workaround) {
        var solution = new RetrievedSolution("symptoms", "root cause", "resolution steps", workaround, "scope", 0.9);
        var source = ChatbotSource.of(solution).numbered(1);

        var result = Citations.parse("Solution [1]", List.of(source));

        assertThat(result.citations())
                .containsExactly(new TicketSolutionCitation(
                        1,
                        new CitedSolution(
                                "symptoms",
                                "root cause",
                                "resolution steps",
                                workaround == null || workaround.isBlank() ? null : workaround,
                                "scope")));
    }

    @Test
    void mapsWikiSourceToDocument_AC_006_4() {
        UUID documentId = UUID.randomUUID();
        var source = ChatbotSource.of(new WikiPassage(documentId, "Policy", "passage", 0.9))
                .numbered(1);

        var result = Citations.parse("Policy [1]", List.of(source));

        assertThat(result.citations()).containsExactly(new WikiDocumentCitation(1, documentId, "Policy"));
    }
}
