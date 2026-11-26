package vn.tsolve.chatbot.answer.service.impl;

import java.util.UUID;
import vn.tsolve.chatbot.answer.dto.ChatbotCitation;
import vn.tsolve.chatbot.answer.dto.CitedSolution;
import vn.tsolve.chatbot.answer.dto.TicketSolutionCitation;
import vn.tsolve.chatbot.answer.dto.WikiDocumentCitation;
import vn.tsolve.chatbot.answer.entity.AnswerSource;
import vn.tsolve.chatbot.source.service.WikiSources.WikiPassage;
import vn.tsolve.search.retrieval.dto.RetrievedSolution;
import vn.tsolve.search.retrieval.dto.RetrievedTicketSolution;

/** {@code id} is the ticket's public id or the Wiki document id, stored but never cited (AC-006.3, AC-014.1). */
public record ChatbotSource(int number, UUID id, double similarity, String text, ChatbotCitation citation) {
    public static ChatbotSource of(RetrievedSolution solution) {
        String workaround = solution.workaround();
        if (workaround != null && workaround.isBlank()) {
            workaround = null;
        }
        var cited = new CitedSolution(
                solution.symptoms(),
                solution.rootCause(),
                solution.resolutionSteps(),
                workaround,
                solution.scopeOfApplicability());
        String text = String.join(
                "\n",
                "Symptoms: " + cited.symptoms(),
                "Root cause: " + cited.rootCause(),
                "Resolution steps: " + cited.resolutionSteps(),
                "Workaround: " + (workaround == null ? "" : workaround),
                "Scope of applicability: " + cited.scopeOfApplicability());
        return new ChatbotSource(
                0, solution.ticketId(), solution.similarity(), text, new TicketSolutionCitation(0, cited));
    }

    /** A Solution of the Project's Department (STORY-012); its ticket id is never cited (AC-006.3). */
    public static ChatbotSource of(RetrievedTicketSolution solution) {
        return of(new RetrievedSolution(
                solution.ticketId(),
                solution.symptoms(),
                solution.rootCause(),
                solution.resolutionSteps(),
                solution.workaround(),
                solution.scopeOfApplicability(),
                solution.similarity()));
    }

    public static ChatbotSource of(WikiPassage passage) {
        return new ChatbotSource(
                0,
                passage.wikiDocumentId(),
                passage.similarity(),
                passage.title() + "\n" + passage.text(),
                new WikiDocumentCitation(0, passage.wikiDocumentId(), passage.title()));
    }

    public ChatbotSource numbered(int number) {
        ChatbotCitation numbered =
                switch (citation) {
                    case TicketSolutionCitation solution -> new TicketSolutionCitation(number, solution.solution());
                    case WikiDocumentCitation wiki ->
                        new WikiDocumentCitation(number, wiki.wikiDocumentId(), wiki.title());
                };
        return new ChatbotSource(number, id, similarity, text, numbered);
    }

    public String kind() {
        return citation instanceof TicketSolutionCitation ? "TICKET" : "WIKI";
    }

    /** The stored record of this kept source (AC-014.1): similarity rounded to 4 decimals. */
    AnswerSource stored(boolean cited) {
        return new AnswerSource(number, kind(), id, Math.round(similarity * 10_000) / 10_000.0, cited);
    }
}
