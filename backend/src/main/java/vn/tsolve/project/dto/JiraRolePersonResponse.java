package vn.tsolve.project.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.UUID;

/** A person of a Jira project role and whether they can be added; the T-Solve fields only when ADDABLE (AC-011.2). */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record JiraRolePersonResponse(
        String jiraAccountId, String displayName, Status status, UUID userId, String fullName, String email) {

    public enum Status {
        ADDABLE,
        NOT_LINKED,
        NOT_TICKET_RESOLVER,
        NOT_ACTIVE,
        ALREADY_MEMBER
    }

    public static JiraRolePersonResponse addable(
            String jiraAccountId, String displayName, UUID userId, String fullName, String email) {
        return new JiraRolePersonResponse(jiraAccountId, displayName, Status.ADDABLE, userId, fullName, email);
    }

    public static JiraRolePersonResponse notAddable(String jiraAccountId, String displayName, Status status) {
        return new JiraRolePersonResponse(jiraAccountId, displayName, status, null, null, null);
    }
}
