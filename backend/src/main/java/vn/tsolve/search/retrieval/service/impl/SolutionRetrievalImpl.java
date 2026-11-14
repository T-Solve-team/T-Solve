package vn.tsolve.search.retrieval.service.impl;

import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.tsolve.search.embedding.repository.PgVector;
import vn.tsolve.search.retrieval.dto.RetrievedSolution;
import vn.tsolve.search.retrieval.dto.RetrievedTicketSolution;
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
        String vector = checkedVector(queryVector, minSimilarity, limit);
        return repository.nearest(vector, minSimilarity, limit).stream()
                .map(row -> new RetrievedSolution(
                        row.getSymptoms(),
                        row.getRootCause(),
                        row.getResolutionSteps(),
                        blankToNull(row.getWorkaround()),
                        row.getScopeOfApplicability(),
                        row.getSimilarity()))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<RetrievedTicketSolution> nearestInProjectDepartment(
            UUID projectId, float[] queryVector, double minSimilarity, int limit) {
        if (projectId == null) {
            throw new IllegalArgumentException("Invalid retrieval arguments");
        }
        String vector = checkedVector(queryVector, minSimilarity, limit);
        return repository.nearestInProjectDepartment(projectId, vector, minSimilarity, limit).stream()
                .map(row -> new RetrievedTicketSolution(
                        row.getTicketId(),
                        row.getSymptoms(),
                        row.getRootCause(),
                        row.getResolutionSteps(),
                        blankToNull(row.getWorkaround()),
                        row.getScopeOfApplicability(),
                        row.getSimilarity()))
                .toList();
    }

    // AC-010.5 / AC-011.4: bad arguments throw before any query; PgVector rejects non-finite components.
    private static String checkedVector(float[] queryVector, double minSimilarity, int limit) {
        if (queryVector == null
                || queryVector.length != 1024
                || !Double.isFinite(minSimilarity)
                || minSimilarity < 0
                || minSimilarity > 1
                || limit < 1) {
            throw new IllegalArgumentException("Invalid retrieval arguments");
        }
        return PgVector.literal(queryVector);
    }

    private static String blankToNull(String text) {
        return text == null || text.isBlank() ? null : text;
    }
}
