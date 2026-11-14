package vn.tsolve.search.retrieval.service;

import java.util.List;
import java.util.UUID;
import vn.tsolve.search.retrieval.dto.RetrievedSolution;
import vn.tsolve.search.retrieval.dto.RetrievedTicketSolution;

public interface SolutionRetrieval {
    /** Workspace Solutions nearest to a 1024-dimensional bge-m3 vector, most similar first. */
    List<RetrievedSolution> nearest(float[] queryVector, double minSimilarity, int limit);

    /**
     * Solutions of the Department that owns the Project with this {@code public_id}, nearest to a 1024-dimensional bge-m3
     * vector, most similar first (AC-011.1 to AC-011.5).
     */
    List<RetrievedTicketSolution> nearestInProjectDepartment(
            UUID projectId, float[] queryVector, double minSimilarity, int limit);
}
