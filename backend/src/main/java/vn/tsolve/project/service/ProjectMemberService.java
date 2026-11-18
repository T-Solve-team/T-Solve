package vn.tsolve.project.service;

import java.util.List;
import java.util.UUID;
import vn.tsolve.project.dto.AddProjectMembersRequest;
import vn.tsolve.project.dto.JiraProjectRoleResponse;
import vn.tsolve.project.dto.JiraRolePeopleResponse;
import vn.tsolve.project.dto.MemberCandidateResponse;
import vn.tsolve.project.dto.ProjectMemberResponse;
import vn.tsolve.project.dto.ProjectSummaryResponse;

/** UC-15, UC-16: the Project Manager of a Project views, adds and removes its members. Every method refuses anyone else. */
public interface ProjectMemberService {

    ProjectSummaryResponse getProject(UUID projectId);

    List<ProjectMemberResponse> listMembers(UUID projectId);

    List<MemberCandidateResponse> searchCandidates(UUID projectId, String q);

    /** All or nothing; answers the full member list after the add. */
    List<ProjectMemberResponse> addMembers(UUID projectId, AddProjectMembersRequest request);

    /** Removes one {@code MEMBER}; answers the full member list after the remove. */
    List<ProjectMemberResponse> removeMember(UUID projectId, UUID userId);

    /** The roles of the Project's Jira project, read as the T-Solve Jira App (STORY-011, AC-011.1). */
    List<JiraProjectRoleResponse> jiraRoles(UUID projectId);

    /** The people of a Jira project role and whether each can be added; changes nothing (AC-011.2, AC-011.3). */
    JiraRolePeopleResponse jiraRolePeople(UUID projectId, long roleId);
}
