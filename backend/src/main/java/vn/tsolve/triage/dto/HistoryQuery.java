package vn.tsolve.triage.dto;

/** The raw list parameters; the service validates them so each failure gets the SRS code the spec names. */
public record HistoryQuery(
        String projectId, String answer, String from, String to, Integer page, Integer size, String sort) {}
