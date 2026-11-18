package vn.tsolve.wiki.service.impl;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.tsolve.chatbot.source.service.WikiSources;
import vn.tsolve.common.security.TSolveUser;
import vn.tsolve.wiki.repository.AccessRepository;

@Service
@Transactional(readOnly = true)
public class WikiSourcesImpl implements WikiSources {
    private final AccessRepository repository;

    public WikiSourcesImpl(AccessRepository repository) {
        this.repository = repository;
    }

    @Override
    public List<WikiPassage> nearest(TSolveUser user, float[] queryVector, double minSimilarity, int limit) {
        return repository.nearestVisible(user.id(), Arrays.toString(queryVector), minSimilarity, limit).stream()
                .map(row -> new WikiPassage(row.getDocumentId(), row.getTitle(), row.getText(), row.getSimilarity()))
                .toList();
    }

    @Override
    public List<WikiPassage> nearestCompanyWide(float[] queryVector, double minSimilarity, int limit) {
        return repository.nearestCompanyWide(Arrays.toString(queryVector), minSimilarity, limit).stream()
                .map(row -> new WikiPassage(row.getDocumentId(), row.getTitle(), row.getText(), row.getSimilarity()))
                .toList();
    }

    @Override
    public Set<UUID> unavailable(TSolveUser user, Set<UUID> wikiDocumentIds) {
        if (wikiDocumentIds.isEmpty()) {
            return Set.of();
        }
        Set<UUID> unavailable = new HashSet<>(wikiDocumentIds);
        unavailable.removeAll(repository.findAvailableDocuments(user.id(), wikiDocumentIds));
        return Set.copyOf(unavailable);
    }
}
