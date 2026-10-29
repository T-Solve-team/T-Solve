package vn.tsolve.ticket.dto;

import java.util.Map;

/** An SRS message to show, by code; the web app fills its template from params. */
public record MessageRef(String code, Map<String, Object> params) {}
