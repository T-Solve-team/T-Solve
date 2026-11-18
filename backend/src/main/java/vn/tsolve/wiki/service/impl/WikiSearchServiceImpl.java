package vn.tsolve.wiki.service.impl;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import vn.tsolve.ai.client.EmbeddingClient;
import vn.tsolve.common.exception.FieldMessageCodeException;
import vn.tsolve.wiki.dto.WikiDocumentSummary;
import vn.tsolve.wiki.repository.WikiSearchRepository;
import vn.tsolve.wiki.service.WikiSearchService;

@Service
public class WikiSearchServiceImpl implements WikiSearchService {
    private static final Logger log = LoggerFactory.getLogger(WikiSearchServiceImpl.class);
    private final WikiSearchRepository repository;
    private final EmbeddingClient embeddings;
    private final double minSimilarity;

    public WikiSearchServiceImpl(
            WikiSearchRepository repository,
            EmbeddingClient embeddings,
            @Value("${tsolve.wiki.search.min-similarity:0.55}") double minSimilarity) {
        this.repository = repository;
        this.embeddings = embeddings;
        this.minSimilarity = minSimilarity;
    }

    @Override
    public List<WikiDocumentSummary> search(UUID userId, String query) {
        if (query == null || query.isBlank()) {
            throw new FieldMessageCodeException(HttpStatus.BAD_REQUEST, "MSG01", Map.of("field_name", "q"), "q");
        }
        if (query.length() > 200) {
            throw new FieldMessageCodeException(
                    HttpStatus.BAD_REQUEST, "MSG02", Map.of("field_name", "q", "max_length", 200), "q");
        }
        // PostgreSQL text rejects NUL; without this check websearch_to_tsquery answers 500 (BUG-290).
        if (query.indexOf('\0') >= 0) {
            throw new FieldMessageCodeException(HttpStatus.BAD_REQUEST, "MSG06", Map.of("field_name", "q"), "q");
        }
        String text = query.strip();
        String vector = null;
        // Embedding is outside the repository's read transaction; a failed AI call still permits keyword search.
        try {
            vector = Arrays.toString(embeddings.embed(List.of(text)).getFirst());
        } catch (RuntimeException failure) {
            log.warn("Wiki search embedding unavailable: {}", failure.getClass().getSimpleName());
        }
        return repository.search(userId, text, vector, minSimilarity);
    }
}
