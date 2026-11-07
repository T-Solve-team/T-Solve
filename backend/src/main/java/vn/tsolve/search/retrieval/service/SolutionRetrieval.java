package vn.tsolve.search.retrieval.service;

import java.util.List;
import vn.tsolve.search.retrieval.dto.RetrievedSolution;

public interface SolutionRetrieval {
    /** Workspace Solutions nearest to a 1024-dimensional bge-m3 vector, most similar first. */
    List<RetrievedSolution> nearest(float[] queryVector, double minSimilarity, int limit);
}
