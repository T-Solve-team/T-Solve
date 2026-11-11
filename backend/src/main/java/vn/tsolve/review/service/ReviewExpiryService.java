package vn.tsolve.review.service;

import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

public interface ReviewExpiryService {
    /** Returns expired ticket counts per Project, or an empty map when none are overdue. */
    Map<UUID, Integer> expireOverdue(LocalDate today);
}
