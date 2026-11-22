package vn.tsolve.jiraapp.dto;

/** Whether the "Ask T-Solve" page can answer in the FIT's Jira project (AC-010.2, AC-010.14). */
public record JiraAskerEligibility(Status status) {

    public enum Status {
        CONNECTED,
        SITE_NOT_CONNECTED,
        PROJECT_NOT_CONNECTED
    }
}
