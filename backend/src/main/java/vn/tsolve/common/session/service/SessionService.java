package vn.tsolve.common.session.service;

import vn.tsolve.common.session.dto.CurrentUserResponse;

public interface SessionService {
    /** The signed-in user, built from the principal and the four lookups. */
    CurrentUserResponse getMe();
}
