package vn.tsolve.review.dto;

/** One advisory finding of AI check (AC-015.4); never stored. */
public record AiReviewFinding(Field field, Severity severity, String issue, String suggestion) {

    public enum Field {
        TITLE,
        DESCRIPTION,
        SYMPTOMS,
        ROOT_CAUSE,
        RESOLUTION_STEPS,
        WORKAROUND,
        SCOPE,
        GENERAL
    }

    /** Declared most severe first: the findings are ordered by it. */
    public enum Severity {
        HIGH,
        MEDIUM,
        LOW
    }
}
