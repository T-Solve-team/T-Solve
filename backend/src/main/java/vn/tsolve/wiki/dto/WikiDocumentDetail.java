package vn.tsolve.wiki.dto;

import java.time.Instant;
import java.util.UUID;
import vn.tsolve.wiki.entity.WikiDocumentType;

public record WikiDocumentDetail(
        UUID documentId,
        String title,
        UUID spaceId,
        String spaceName,
        UUID folderId,
        String folderName,
        String visibility,
        WikiDocumentType documentType,
        String owner,
        Instant updatedAt,
        String content,
        String fileName,
        long fileSize,
        boolean canDelete,
        boolean inTrash) {}
