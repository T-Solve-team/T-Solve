package vn.tsolve.jiraapp.service;

import vn.tsolve.common.security.JiraAppPrincipal;
import vn.tsolve.jiraapp.dto.JiraAccountLinkStart;
import vn.tsolve.jiraapp.dto.JiraAccountLinkStatus;

/** The "T-Solve account" settings page: the link status and starting a link (jira-app STORY-014). */
public interface JiraAccountLinkPageService {

    /** LINKED, NOT_LINKED or SITE_NOT_CONNECTED for the FIT's site and Jira user (AC-014.2). */
    JiraAccountLinkStatus status(JiraAppPrincipal caller);

    /** A link request for the FIT's Jira user and the link to open (AC-014.6, AC-014.9, AC-014.10). */
    JiraAccountLinkStart start(JiraAppPrincipal caller);
}
