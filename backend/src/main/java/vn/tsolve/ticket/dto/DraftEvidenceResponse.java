package vn.tsolve.ticket.dto;

import java.util.UUID;

/** One Evidence file of a Manual Draft; its storage key never leaves the backend. */
public record DraftEvidenceResponse(UUID id, String fileName, String fileType, long fileSize) {}
