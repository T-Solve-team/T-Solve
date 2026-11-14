package vn.tsolve.ticket.dto;

import java.util.UUID;

public record ManualEvidence(UUID evidenceId, String fileName, long fileSize) {}
