package vn.tsolve.tracker.dto;

/** A comment of an issue as plain text, with its author's account id (STORY-014). */
public record TrackerComment(String authorAccountId, String text) {}
