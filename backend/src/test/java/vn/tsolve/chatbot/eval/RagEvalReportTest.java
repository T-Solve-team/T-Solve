package vn.tsolve.chatbot.eval;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import vn.tsolve.chatbot.answer.dto.TicketSolutionCitation;
import vn.tsolve.chatbot.answer.dto.WikiDocumentCitation;
import vn.tsolve.chatbot.answer.service.impl.ChatbotSource;
import vn.tsolve.chatbot.eval.GoldFile.GoldLine;
import vn.tsolve.chatbot.eval.GoldFile.Kind;
import vn.tsolve.chatbot.eval.GoldFile.Source;

class RagEvalReportTest {
    private static final UUID ID = new UUID(0, 1);
    private static final UUID OTHER = new UUID(0, 2);

    @Test
    void reportsRankSimilarityAndTop_AC_015_3() {
        var line = new GoldLine(
                2, "Question", List.of(new Source(Kind.TICKET, ID), new Source(Kind.WIKI, OTHER)), Map.of());
        var report = RagEvalReport.of(
                List.of(line, line(4, Map.of()), line(7, Map.of())),
                List.of(
                        List.of(wiki(ID, 0.91236), wiki(OTHER, 0.83456), ticket(ID, 0.73)),
                        List.of(wiki(ID, 0.9), wiki(OTHER, 0.8), wiki(OTHER, 0.7), ticket(ID, 0.6)),
                        List.of()),
                0.7,
                2,
                3);

        assertThat(report.minSimilarity()).isEqualTo(0.7);
        assertThat(report.sourceLimit()).isEqualTo(2);
        assertThat(report.k()).isEqualTo(3);
        assertThat(report.lines())
                .containsExactly(
                        new RagEvalReport.Line(2, 2, 0.8346, 0.9124),
                        new RagEvalReport.Line(4, null, null, 0.9),
                        new RagEvalReport.Line(7, null, null, null));
    }

    @Test
    void scoresRecallWithinKeptSources_AC_015_4() {
        var report = RagEvalReport.of(
                List.of(line(1, Map.of()), line(2, Map.of()), line(3, Map.of())),
                List.of(
                        List.of(wiki(OTHER, 0.9), ticket(ID, 0.7)),
                        List.of(wiki(OTHER, 0.9), wiki(ID, 0.8), ticket(ID, 0.75)),
                        List.of(ticket(ID, 0.69999))),
                0.7,
                2,
                10);

        assertThat(report.overall().recall()).isEqualTo(0.3333);
    }

    @Test
    void scoresMrrAndNoSourceRate_AC_015_4() {
        var report = RagEvalReport.of(
                List.of(line(1, Map.of()), line(2, Map.of()), line(3, Map.of())),
                List.of(List.of(wiki(OTHER, 0.695), ticket(ID, 0.6)), List.of(), List.of(wiki(OTHER, 0.7))),
                0.7,
                1,
                10);

        assertThat(report.overall()).isEqualTo(new RagEvalReport.Scores(3, 0, 0.1667, 0.6667));
        var emptyWorkspace = RagEvalReport.of(List.of(line(1, Map.of())), List.of(List.of()), 0.7, 1, 10);
        assertThat(emptyWorkspace.overall()).isEqualTo(new RagEvalReport.Scores(1, 0, 0, 1));
    }

    @Test
    void scoresEachGroupValue_AC_015_4() {
        var report = RagEvalReport.of(
                List.of(
                        line(1, Map.of("team", "IT", "source", "ticket")),
                        line(2, Map.of("team", "IT")),
                        line(3, Map.of("team", "Support", "source", "ticket"))),
                List.of(List.of(ticket(ID, 0.8)), List.of(), List.of(wiki(OTHER, 0.7), ticket(ID, 0.65))),
                0.7,
                1,
                10);

        assertThat(report.groups())
                .containsExactlyInAnyOrderEntriesOf(Map.of(
                        "team=IT", new RagEvalReport.Scores(2, 0.5, 0.5, 0.5),
                        "team=Support", new RagEvalReport.Scores(1, 0, 0.5, 0),
                        "source=ticket", new RagEvalReport.Scores(2, 0.5, 0.75, 0)));
    }

    private static GoldLine line(int number, Map<String, String> groups) {
        return new GoldLine(number, "Question", List.of(new Source(Kind.TICKET, ID)), groups);
    }

    private static ChatbotSource ticket(UUID id, double similarity) {
        return new ChatbotSource(0, id, similarity, "Ticket text", new TicketSolutionCitation(0, null));
    }

    private static ChatbotSource wiki(UUID id, double similarity) {
        return new ChatbotSource(0, id, similarity, "Wiki text", new WikiDocumentCitation(0, id, "Title"));
    }
}
