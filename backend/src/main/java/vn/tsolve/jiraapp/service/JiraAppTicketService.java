package vn.tsolve.jiraapp.service;

import java.util.List;
import java.util.UUID;
import vn.tsolve.common.security.JiraAppPrincipal;
import vn.tsolve.jiraapp.dto.JiraAppTicketPreview;
import vn.tsolve.jiraapp.dto.JiraAppTicketResult;

/** Published tickets in the Issue Panel: search and preview (jira-app STORY-008, STORY-009). */
public interface JiraAppTicketService {

    /** The Published tickets of the user's Departments matching {@code q}, best first, at most 20 (AC-008.4). */
    List<JiraAppTicketResult> search(JiraAppPrincipal caller, String q);

    /** One Published ticket of the user's Departments; 403 MSG08 otherwise (AC-009.1, AC-009.2). */
    JiraAppTicketPreview preview(JiraAppPrincipal caller, UUID ticketId);
}
