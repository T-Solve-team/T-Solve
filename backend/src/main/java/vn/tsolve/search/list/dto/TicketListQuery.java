package vn.tsolve.search.list.dto;

/** Raw filters; the service validates them before resolving access or querying tickets. */
public record TicketListQuery(
        String keyword, String departmentId, String source, String status, Integer page, Integer size, String sort) {}
