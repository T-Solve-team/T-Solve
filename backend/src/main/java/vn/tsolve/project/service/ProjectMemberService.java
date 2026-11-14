package vn.tsolve.project.service;

import java.util.List;
import java.util.UUID;
import vn.tsolve.project.dto.AddProjectMembersRequest;
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
}
