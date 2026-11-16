package vn.tsolve.wiki.service.impl;

import java.util.Arrays;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.tsolve.triage.dto.WikiSource;
import vn.tsolve.triage.service.WikiSourceRetrieval;
import vn.tsolve.wiki.repository.AccessRepository;

@Service
@Transactional(readOnly = true)
public class WikiSourceRetrievalImpl implements WikiSourceRetrieval {
    private final AccessRepository repository;

    public WikiSourceRetrievalImpl(AccessRepository repository) {
        this.repository = repository;
    }

    @Override
    public List<WikiSource> nearest(float[] queryVector, double minSimilarity, int limit) {
        return repository.nearestCompanyWide(Arrays.toString(queryVector), minSimilarity, limit).stream()
                .map(row -> new WikiSource(
                        row.getDocumentId().toString(), row.getTitle(), row.getText(), row.getSimilarity()))
                .toList();
    }
}
