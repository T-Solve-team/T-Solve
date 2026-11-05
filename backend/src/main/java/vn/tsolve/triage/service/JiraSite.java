package vn.tsolve.triage.service;

import java.util.Optional;

/** The Workspace's Jira site URL ({@code workspace.jira_site_url}); empty when the Workspace has none (AC-003.6). */
public interface JiraSite {

    Optional<String> url();
}
