package vn.tsolve.ai.dto;

import java.util.List;

/** Issue comments are oldest first. */
public record IssueContent(String summary, String description, List<String> comments) {}
