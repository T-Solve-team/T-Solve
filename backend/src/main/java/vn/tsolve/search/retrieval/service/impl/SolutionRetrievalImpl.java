package vn.tsolve.search.retrieval.service.impl;

import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.tsolve.search.embedding.repository.PgVector;
import vn.tsolve.search.retrieval.dto.RetrievedSolution;
import vn.tsolve.search.retrieval.repository.SolutionRetrievalRepository;
import vn.tsolve.search.retrieval.service.SolutionRetrieval;

@Service
public class SolutionRetrievalImpl implements SolutionRetrieval {
    private final SolutionRetrievalRepository repository;

    public SolutionRetrievalImpl(SolutionRetrievalRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional(readOnly = true)
    public List<RetrievedSolution> nearest(float[] queryVector, double minSimilarity, int limit) {
        if (queryVector == null
                || queryVector.length != 1024
                || !Double.isFinite(minSimilarity)
                || minSimilarity < 0
                || minSimilarity > 1
                || limit < 1) {
            throw new IllegalArgumentException("Invalid retrieval arguments");
        }
        return repository.nearest(PgVector.literal(queryVector), minSimilarity, limit).stream()
                .map(row -> new RetrievedSolution(
                        row.getSymptoms(),
                        row.getRootCause(),
                        row.getResolutionSteps(),
                        row.getWorkaround() == null || row.getWorkaround().isBlank() ? null : row.getWorkaround(),
                        row.getScopeOfApplicability(),
                        row.getSimilarity()))
                .toList();
    }
}
