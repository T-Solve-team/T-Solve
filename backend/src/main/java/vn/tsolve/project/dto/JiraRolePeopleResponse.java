package vn.tsolve.project.dto;

import java.util.List;

/** The people of a Jira project role, and the names of its group actors, never expanded (AC-011.2, AC-011.3). */
public record JiraRolePeopleResponse(List<JiraRolePersonResponse> people, List<String> groups) {

    public JiraRolePeopleResponse {
        people = List.copyOf(people);
        groups = List.copyOf(groups);
    }
}
