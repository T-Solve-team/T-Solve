package vn.tsolve.ticket.service;

import java.time.Instant;

public interface AuditService {
    void record(String actor, String action, String resource, Instant at);
}
