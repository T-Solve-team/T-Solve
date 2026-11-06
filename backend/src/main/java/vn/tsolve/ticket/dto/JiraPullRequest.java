package vn.tsolve.ticket.dto;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.UUID;

/** Dates may be null here: the service answers a missing one with MSG01 before anything else (AC-001.4). */
public record JiraPullRequest(@NotNull UUID projectId, LocalDate resolvedFrom, LocalDate resolvedTo) {}
