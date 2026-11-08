package vn.tsolve.identity.account.service;

import java.util.Optional;
import java.util.UUID;
import vn.tsolve.common.security.TSolveUser;

/** Reads an Account of the current Workspace for other modules, which may not use the identity repository. */
public interface AccountLookupService {

    /** The Account as a user (Employee only, until identity serves roles), or empty when unknown or deactivated. */
    Optional<TSolveUser> findActive(UUID accountId);
}
