package vn.tsolve.ticket.service;

import vn.tsolve.common.security.TSolveUser;
import vn.tsolve.ticket.dto.ManualEntryResponse;

public interface ManualEntryService {

    /**
     * Saves one ticket its Project Manager entered by hand as a Published Ticket with an approve decision by them
     * (UC-22, AC-006.1…AC-006.11). {@code entry} is the JSON text of a {@code ManualEntryRequest}.
     */
    ManualEntryResponse save(TSolveUser user, String entry);
}
