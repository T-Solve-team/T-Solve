package vn.tsolve.wiki.service;

import java.util.List;
import java.util.UUID;
import vn.tsolve.wiki.dto.WikiDocumentSummary;

public interface WikiSearchService {
    List<WikiDocumentSummary> search(UUID userId, String query);
}
