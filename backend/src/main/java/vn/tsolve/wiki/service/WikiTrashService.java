package vn.tsolve.wiki.service;

import java.util.List;
import java.util.UUID;
import vn.tsolve.wiki.dto.WikiRestoreResult;
import vn.tsolve.wiki.dto.WikiTrashItem;

public interface WikiTrashService {
    void trash(UUID userId, UUID documentId);

    List<WikiTrashItem> listTrash(UUID userId, UUID spaceId);

    WikiRestoreResult restore(UUID userId, UUID documentId);

    void deletePermanently(UUID userId, UUID documentId);
}
