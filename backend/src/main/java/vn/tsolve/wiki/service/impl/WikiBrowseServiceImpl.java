package vn.tsolve.wiki.service.impl;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.tsolve.common.exception.NotFoundException;
import vn.tsolve.wiki.dto.WikiDocumentDetail;
import vn.tsolve.wiki.dto.WikiDocumentSummary;
import vn.tsolve.wiki.dto.WikiFolder;
import vn.tsolve.wiki.dto.WikiSpace;
import vn.tsolve.wiki.repository.WikiSearchRepository;
import vn.tsolve.wiki.service.WikiBrowseService;

@Service
@Transactional(readOnly = true)
public class WikiBrowseServiceImpl implements WikiBrowseService {
    private final WikiSearchRepository repository;

    public WikiBrowseServiceImpl(WikiSearchRepository repository) {
        this.repository = repository;
    }

    @Override
    public List<WikiSpace> listSpaces(UUID userId) {
        return repository.listSpaces(userId);
    }

    @Override
    public List<WikiDocumentSummary> listCompanyWide(UUID userId) {
        return repository.listCompanyWide(userId);
    }

    @Override
    public List<WikiFolder> listFolders(UUID userId, UUID spaceId) {
        return repository.listFolders(requireViewableSpace(userId, spaceId));
    }

    @Override
    public List<WikiDocumentSummary> listDocuments(UUID userId, UUID spaceId, UUID folderId) {
        long internalId = requireViewableSpace(userId, spaceId);
        if (folderId != null && !repository.isFolderInSpace(internalId, folderId)) {
            throw new NotFoundException("MSG04", Map.of());
        }
        return repository.listDocuments(userId, internalId, folderId);
    }

    @Override
    public WikiDocumentDetail getDocument(UUID userId, UUID documentId) {
        return repository.findDocument(userId, documentId).orElseThrow(() -> new NotFoundException("MSG110", Map.of()));
    }

    private long requireViewableSpace(UUID userId, UUID spaceId) {
        var space = repository.findSpace(userId, spaceId).orElseThrow(() -> new NotFoundException("MSG04", Map.of()));
        if (!space.viewable()) {
            throw new AccessDeniedException("MSG08");
        }
        return space.spaceId();
    }
}
