package vn.tsolve.chatbot.answer.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;
import org.springframework.stereotype.Component;
import vn.tsolve.chatbot.answer.dto.TicketSolutionCitation;
import vn.tsolve.chatbot.answer.service.impl.ChatbotSource;
import vn.tsolve.chatbot.source.service.WikiSources.WikiPassage;
import vn.tsolve.search.retrieval.dto.RetrievedSolution;
import vn.tsolve.search.retrieval.dto.RetrievedTicketSolution;

@Component
public class ChatbotRetrieval {

    public List<ChatbotSource> rank(List<RetrievedSolution> solutions, List<WikiPassage> passages, int limit) {
        return rankSources(solutions.stream().map(ChatbotSource::of), passages, limit);
    }

    public List<ChatbotSource> rankProject(
            List<RetrievedTicketSolution> solutions, List<WikiPassage> passages, int limit) {
        return rankSources(solutions.stream().map(ChatbotSource::of), passages, limit);
    }

    private List<ChatbotSource> rankSources(Stream<ChatbotSource> tickets, List<WikiPassage> passages, int limit) {
        List<ChatbotSource> candidates = new ArrayList<>();
        tickets.forEach(candidates::add);
        passages.stream().map(ChatbotSource::of).forEach(candidates::add);
        candidates.sort(Comparator.comparingDouble(ChatbotSource::similarity)
                .reversed()
                .thenComparingInt(source -> source.citation() instanceof TicketSolutionCitation ? 0 : 1));
        List<ChatbotSource> kept = new ArrayList<>();
        for (int index = 0; index < Math.min(limit, candidates.size()); index++) {
            kept.add(candidates.get(index).numbered(index + 1));
        }
        return kept;
    }
}
