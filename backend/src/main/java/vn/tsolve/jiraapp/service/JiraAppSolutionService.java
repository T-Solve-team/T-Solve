package vn.tsolve.jiraapp.service;

import vn.tsolve.common.security.JiraAppPrincipal;
import vn.tsolve.jiraapp.dto.IssuePanelResponse;
import vn.tsolve.jiraapp.dto.SolutionFormRequest;
import vn.tsolve.jiraapp.dto.SolutionFormResponse;
import vn.tsolve.jiraapp.dto.SubmitSolutionRequest;
import vn.tsolve.jiraapp.dto.SubmittedTicketResponse;
import vn.tsolve.ticket.dto.SolutionSections;

/** The Solution Form and the Issue Panel status of one Jira issue (jira-app STORY-001 to STORY-008). */
public interface JiraAppSolutionService {

    /** Whether the form may open, and its content: Draft, else AI draft, else the issue text with MSG67. */
    SolutionFormResponse openForm(JiraAppPrincipal caller, String issueKey, SolutionFormRequest request);

    /** Finish: creates the issue's Solution Form ticket for the Review Queue (AC-004.1). */
    SubmittedTicketResponse submit(JiraAppPrincipal caller, String issueKey, SubmitSolutionRequest request);

    /** Keeps the Draft when the form is closed (AC-005.1). */
    void saveDraft(JiraAppPrincipal caller, String issueKey, SolutionSections sections);

    /** Rewrite with AI (AC-007.1); 503 MSG68 when it fails (AC-007.4). */
    SolutionSections rewrite(JiraAppPrincipal caller, SolutionSections sections);

    /** What the Issue Panel shows (AC-001.4, AC-008.1 to AC-008.3, AC-008.6). */
    IssuePanelResponse panel(JiraAppPrincipal caller, String issueKey);
}
