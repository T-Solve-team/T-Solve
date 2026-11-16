package vn.tsolve.wiki.service;

import java.util.List;
import java.util.UUID;
import vn.tsolve.wiki.dto.WikiDocumentDetail;
import vn.tsolve.wiki.dto.WikiDocumentSummary;
import vn.tsolve.wiki.dto.WikiFolder;
import vn.tsolve.wiki.dto.WikiSpace;

public interface WikiBrowseService {
    List<WikiSpace> listSpaces(UUID userId);

    List<WikiDocumentSummary> listCompanyWide(UUID userId);

    List<WikiFolder> listFolders(UUID userId, UUID spaceId);

    List<WikiDocumentSummary> listDocuments(UUID userId, UUID spaceId, UUID folderId);

    WikiDocumentDetail getDocument(UUID userId, UUID documentId);
}
