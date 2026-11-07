package vn.tsolve.tracker.service;

import java.util.Optional;
import vn.tsolve.tracker.dto.JiraAccount;

/**
 * The current Workspace's Jira service account, owned by {@code workspace} (UC-08/09: {@code
 * workspace.service_account_email} and its encrypted token). Declared here because the tracker calls it
 * (STORY-012); a configured stand-in answers until {@code workspace} implements it.
 */
public interface JiraServiceAccount {

    /** The service account, or empty when the Workspace has none yet. */
    Optional<JiraAccount> current();
}
