package vn.tsolve.triage.service;

import java.util.List;
import vn.tsolve.triage.dto.WikiSource;

/** Declared by triage, implemented later by the wiki module; until then {@code NoWikiSources} answers (AC-005.12). */
public interface WikiSourceRetrieval {
    /** Company-wide Wiki documents nearest to queryVector (cosine, 0..1, same scale as tickets), most similar first. */
    List<WikiSource> nearest(float[] queryVector, double minSimilarity, int limit);
}
