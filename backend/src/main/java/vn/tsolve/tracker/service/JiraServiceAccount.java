package vn.tsolve.tracker.service;

import java.util.Optional;
import vn.tsolve.tracker.dto.JiraAccount;

/**
 * The current Workspace's Jira service account, implemented by {@code workspace} from the bound tenant's stored
 * site, email and encrypted token (UC-10/11). Declared here because the tracker calls it (STORY-012).
 */
public interface JiraServiceAccount {

    /** The service account, or empty when it is absent, incomplete or its token cannot be decrypted. */
    Optional<JiraAccount> current();
}
