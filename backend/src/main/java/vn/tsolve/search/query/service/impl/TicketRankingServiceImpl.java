package vn.tsolve.search.query.service.impl;

import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import vn.tsolve.ai.client.EmbeddingClient;
import vn.tsolve.ai.exception.AiUnavailableException;
import vn.tsolve.search.embedding.repository.PgVector;
import vn.tsolve.search.query.dto.RankFilter;
import vn.tsolve.search.query.dto.RankedPage;
import vn.tsolve.search.query.mapper.TicketSearchResultMapper;
import vn.tsolve.search.query.repository.TicketRankingRepository;
import vn.tsolve.search.query.repository.TicketRankingRepository.RankedRow;
import vn.tsolve.search.query.service.TicketRankingService;
import vn.tsolve.search.scope.service.VisibleScope.Scope;

@Service
public class TicketRankingServiceImpl implements TicketRankingService {
    private final EmbeddingClient embeddings;
    private final TicketRankingRepository repository;
    private final TicketSearchResultMapper mapper;
    private final double minSimilarity;

    public TicketRankingServiceImpl(
            EmbeddingClient embeddings,
            TicketRankingRepository repository,
            TicketSearchResultMapper mapper,
            @Value("${SEARCH_MIN_SIMILARITY:0.5}") double minSimilarity) {
        this.embeddings = embeddings;
        this.repository = repository;
        this.mapper = mapper;
        this.minSimilarity = minSimilarity;
    }

    @Override
    public RankedPage rank(String text, String keywordText, RankFilter filter, Scope scope, Pageable page) {
        if (scope.departmentIds().isEmpty()) return new RankedPage(Page.empty(page), false);

        String department =
                filter.departmentId() == null ? null : filter.departmentId().toString();
        String from =
                filter.resolvedFrom() == null ? null : filter.resolvedFrom().toString();
        String to = filter.resolvedTo() == null ? null : filter.resolvedTo().toString();
        List<float[]> vectors;
        try {
            vectors = embeddings.embed(List.of(text));
        } catch (AiUnavailableException unavailable) {
            Page<RankedRow> rows = repository.keyword(
                    scope.departmentIds(), department, filter.source(), from, to, keyword(keywordText), page);
            return new RankedPage(rows.map(mapper::toResponse), true);
        }
        Page<RankedRow> rows = repository.semantic(
                scope.departmentIds(),
                department,
                filter.source(),
                from,
                to,
                PgVector.literal(vectors.getFirst()),
                minSimilarity,
                page);
        return new RankedPage(rows.map(mapper::toResponse), false);
    }

    private static String keyword(String text) {
        return "%" + text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
    }
}
