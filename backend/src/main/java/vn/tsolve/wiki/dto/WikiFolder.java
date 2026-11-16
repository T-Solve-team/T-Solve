package vn.tsolve.wiki.dto;

import java.util.UUID;

public record WikiFolder(UUID folderId, UUID parentId, String name, int level) {}
