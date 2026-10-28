package vn.tsolve.ticket.csv;

import java.util.Map;

/**
 * One failed rule of a CSV row: an SRS message code, the column it is about (null for the whole Solution) and the
 * values for the message template.
 */
public record RowError(String code, String field, Map<String, Object> params) {}
