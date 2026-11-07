package vn.tsolve.triage.dto;

import java.util.UUID;

/** A Project the caller manages, identified by the UUID the web app knows. */
public record ManagedProject(UUID id, String key) {}
