package vn.tsolve.common.session.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * The My Solutions filters, AND-combined; every field is nullable and null means "no filter". {@code updatedFrom} is
 * inclusive and {@code updatedBefore} exclusive.
 */
public record MySolutionFilter(TicketStatus status, UUID projectId, Instant updatedFrom, Instant updatedBefore) {}
