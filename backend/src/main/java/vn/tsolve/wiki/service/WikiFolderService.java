package vn.tsolve.wiki.service;

import java.util.UUID;
import vn.tsolve.wiki.dto.CreateWikiFolderRequest;
import vn.tsolve.wiki.dto.UpdateWikiFolderRequest;
import vn.tsolve.wiki.dto.WikiFolder;

public interface WikiFolderService {
    WikiFolder create(UUID userId, UUID spaceId, CreateWikiFolderRequest request);

    WikiFolder update(UUID userId, UUID folderId, UpdateWikiFolderRequest request);

    void delete(UUID userId, UUID folderId);
}
