package vn.tsolve.wiki.dto;

import java.time.Instant;
import java.util.UUID;

public record WikiTrashItem(
        UUID documentId, String title, String folderName, String owner, String deletedBy, Instant deletedAt) {}
