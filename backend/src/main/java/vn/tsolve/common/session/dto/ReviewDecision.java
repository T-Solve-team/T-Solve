package vn.tsolve.common.session.dto;

/** The review decision of the API ({@code ReviewDecision} in the YAML); the ticket module maps its own to these. */
public enum ReviewDecision {
    APPROVE,
    REQUEST_CHANGES,
    REJECT
}
