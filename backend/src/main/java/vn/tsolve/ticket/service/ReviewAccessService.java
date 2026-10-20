package vn.tsolve.ticket.service;

import vn.tsolve.ticket.dto.ReviewActor;

/** The integrating identity module must revalidate active account and Project rights on every call. */
public interface ReviewAccessService {
    ReviewActor currentActor();
}
