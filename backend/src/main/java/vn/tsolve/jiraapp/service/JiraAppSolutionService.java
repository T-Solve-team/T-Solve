package vn.tsolve.jiraapp.service;

import java.util.UUID;
import vn.tsolve.common.security.JiraAppPrincipal;
import vn.tsolve.jiraapp.dto.IssuePanelResponse;
import vn.tsolve.jiraapp.dto.SolutionFormRequest;
import vn.tsolve.jiraapp.dto.SolutionFormResponse;
import vn.tsolve.jiraapp.dto.SolutionRewriteJob;
import vn.tsolve.jiraapp.dto.SolutionRewriteJobResult;
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

    /** Starts the rewrite without waiting for AI (AC-007.5); 503 MSG68 if no job can start. */
    SolutionRewriteJob startRewrite(JiraAppPrincipal caller, SolutionSections sections);

    /** Only the Jira account and site that started the job may read it (AC-007.6). */
    SolutionRewriteJobResult rewriteResult(JiraAppPrincipal caller, UUID jobId);

    /** What the Issue Panel shows (AC-001.4, AC-008.1 to AC-008.3). */
    IssuePanelResponse panel(JiraAppPrincipal caller, String issueKey);
}
