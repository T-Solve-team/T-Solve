package vn.tsolve.common.security;

import java.util.Optional;

/**
 * Finds the Workspace whose registered Jira site has a cloudId. Implemented by the tracker module, so that
 * {@code common} depends on no module.
 */
public interface JiraSiteLookup {

    Optional<Long> findWorkspaceId(String cloudId);
}
