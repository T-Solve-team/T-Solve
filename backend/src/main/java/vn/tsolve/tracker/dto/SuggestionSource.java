package vn.tsolve.tracker.dto;

/**
 * One source the suggestion used, stored in {@code suggestion_result.sources_used} in the shape triage reads
 * (KAN-149): {@code type} is {@code TICKET} or {@code WIKI}.
 */
public record SuggestionSource(String type, String id, String title, Double similarity) {}
