package vn.tsolve.review.service;

import vn.tsolve.review.dto.ReviewActor;

/** The integrating identity module must revalidate active account and Project rights on every call. */
public interface ReviewAccessService {
    ReviewActor currentActor();
}
