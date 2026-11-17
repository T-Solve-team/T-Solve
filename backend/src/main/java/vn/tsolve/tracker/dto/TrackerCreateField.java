package vn.tsolve.tracker.dto;

/** One field of a Jira create screen for an issue type. */
public record TrackerCreateField(String fieldId, boolean required, boolean hasDefaultValue) {}
