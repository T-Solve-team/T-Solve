package vn.tsolve.jiraapp.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;

/** Whether the FIT's Jira user is linked to a T-Solve Account, and to whom (jira-app AC-014.2). */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record JiraAccountLinkStatus(Status status, String fullName, String email, Instant linkedAt) {

    public enum Status {
        LINKED,
        NOT_LINKED,
        SITE_NOT_CONNECTED
    }

    public static JiraAccountLinkStatus linked(String fullName, String email, Instant linkedAt) {
        return new JiraAccountLinkStatus(Status.LINKED, fullName, email, linkedAt);
    }

    public static JiraAccountLinkStatus of(Status status) {
        return new JiraAccountLinkStatus(status, null, null, null);
    }
}
