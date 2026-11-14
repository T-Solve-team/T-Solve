package vn.tsolve.ticket.entity;

import java.util.UUID;

/** One Evidence file of a Manual Draft, kept in the Draft's JSON until the Draft becomes a Ticket (AC-007.6). */
public record DraftEvidence(UUID id, String storageKey, String fileName, String fileType, long fileSize) {}
