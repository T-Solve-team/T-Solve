package vn.tsolve.triage.service.impl;

import java.util.List;
import org.springframework.stereotype.Service;
import vn.tsolve.triage.dto.WikiSource;
import vn.tsolve.triage.service.WikiSourceRetrieval;

/** Stand-in until the wiki module implements {@link WikiSourceRetrieval}: no document, so tickets only (AC-005.12). */
// Named: the chatbot module has a stand-in with the same simple name.
@Service("triageNoWikiSources")
public class NoWikiSources implements WikiSourceRetrieval {

    @Override
    public List<WikiSource> nearest(float[] queryVector, double minSimilarity, int limit) {
        return List.of();
    }
}
