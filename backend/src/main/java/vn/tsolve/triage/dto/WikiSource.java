package vn.tsolve.triage.dto;

/** A Company-wide Wiki document found for an issue; {@code content} is plain text, similarity is cosine 0..1. */
public record WikiSource(String documentId, String title, String content, double similarity) {}
