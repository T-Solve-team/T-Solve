package vn.tsolve.wiki.dto;

import java.time.Instant;
import java.util.UUID;

public record WikiDocumentDetail(
        UUID documentId,
        String title,
        UUID spaceId,
        String spaceName,
        UUID folderId,
        String folderName,
        String visibility,
        String owner,
        Instant updatedAt,
        String content,
        String fileName,
        long fileSize,
        boolean canDelete,
        boolean inTrash) {}
