package vn.tsolve.chatbot.source.service.impl;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import vn.tsolve.chatbot.source.service.WikiSources;
import vn.tsolve.common.security.TSolveUser;

/** Fail closed until wiki implements this port; delete this stand-in when that implementation arrives. */
@Service
public class NoWikiSources implements WikiSources {
    @Override
    public List<WikiPassage> nearest(TSolveUser user, float[] queryVector, double minSimilarity, int limit) {
        return List.of();
    }

    @Override
    public Set<UUID> unavailable(TSolveUser user, Set<UUID> wikiDocumentIds) {
        return Set.copyOf(wikiDocumentIds);
    }
}
