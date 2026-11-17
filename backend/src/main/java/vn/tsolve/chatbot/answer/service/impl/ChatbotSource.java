package vn.tsolve.chatbot.answer.service.impl;

import vn.tsolve.chatbot.answer.dto.ChatbotCitation;
import vn.tsolve.chatbot.answer.dto.CitedSolution;
import vn.tsolve.chatbot.answer.dto.TicketSolutionCitation;
import vn.tsolve.chatbot.answer.dto.WikiDocumentCitation;
import vn.tsolve.chatbot.source.service.WikiSources.WikiPassage;
import vn.tsolve.search.retrieval.dto.RetrievedSolution;
import vn.tsolve.search.retrieval.dto.RetrievedTicketSolution;

record ChatbotSource(int number, double similarity, String text, ChatbotCitation citation) {
    static ChatbotSource of(RetrievedSolution solution) {
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
        return new ChatbotSource(0, solution.similarity(), text, new TicketSolutionCitation(0, cited));
    }

    /** A Solution of the Project's Department (STORY-012); its ticket id is never cited (AC-006.3). */
    static ChatbotSource of(RetrievedTicketSolution solution) {
        return of(new RetrievedSolution(
                solution.symptoms(),
                solution.rootCause(),
                solution.resolutionSteps(),
                solution.workaround(),
                solution.scopeOfApplicability(),
                solution.similarity()));
    }

    static ChatbotSource of(WikiPassage passage) {
        return new ChatbotSource(
                0,
                passage.similarity(),
                passage.title() + "\n" + passage.text(),
                new WikiDocumentCitation(0, passage.wikiDocumentId(), passage.title()));
    }

    ChatbotSource numbered(int number) {
        ChatbotCitation numbered =
                switch (citation) {
                    case TicketSolutionCitation solution -> new TicketSolutionCitation(number, solution.solution());
                    case WikiDocumentCitation wiki ->
                        new WikiDocumentCitation(number, wiki.wikiDocumentId(), wiki.title());
                };
        return new ChatbotSource(number, similarity, text, numbered);
    }
}
