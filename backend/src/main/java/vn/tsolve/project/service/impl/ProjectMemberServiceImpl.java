package vn.tsolve.project.service.impl;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.tsolve.common.audit.AuditAction;
import vn.tsolve.common.audit.AuditService;
import vn.tsolve.common.exception.BusinessRuleException;
import vn.tsolve.common.exception.FieldMessageCodeException;
import vn.tsolve.common.exception.NotFoundException;
import vn.tsolve.common.security.TSolveUser;
import vn.tsolve.identity.signin.port.TenantScope;
import vn.tsolve.project.dto.AddProjectMembersRequest;
import vn.tsolve.project.dto.MemberCandidateResponse;
import vn.tsolve.project.dto.ProjectMemberResponse;
import vn.tsolve.project.dto.ProjectSummaryResponse;
import vn.tsolve.project.entity.ProjectStatus;
import vn.tsolve.project.repository.ProjectMemberRepository;
import vn.tsolve.project.service.ProjectMemberService;

@Service
public class ProjectMemberServiceImpl implements ProjectMemberService {
    static final int MAX_QUERY_LENGTH = 100;

    private final ProjectMemberRepository repository;
    private final AuditService audit;
    private final TenantScope scope;

    public ProjectMemberServiceImpl(ProjectMemberRepository repository, AuditService audit, TenantScope scope) {
        this.repository = repository;
        this.audit = audit;
        this.scope = scope;
    }

    @Override
    @Transactional(readOnly = true)
    public ProjectSummaryResponse getProject(UUID projectId) {
        return requireManagedProject(projectId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProjectMemberResponse> listMembers(UUID projectId) {
        requireManagedProject(projectId);
        return repository.members(projectId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<MemberCandidateResponse> searchCandidates(UUID projectId, String q) {
        requireManagedProject(projectId);
        String search = q == null ? "" : q.trim();
        if (search.length() > MAX_QUERY_LENGTH) {
            throw new FieldMessageCodeException(
                    HttpStatus.BAD_REQUEST,
                    "MSG02",
                    Map.of("field_name", "Search", "max_length", MAX_QUERY_LENGTH),
                    "q");
        }
        return repository.candidates(projectId, search);
    }

    @Override
    @Transactional
    public List<ProjectMemberResponse> addMembers(UUID projectId, AddProjectMembersRequest request) {
        ProjectSummaryResponse project = requireManagedProject(projectId);
        List<UUID> userIds = request.userIds();
        if (new HashSet<>(userIds).size() != userIds.size()) {
            throw new FieldMessageCodeException(HttpStatus.BAD_REQUEST, "MSG06", Map.of(), "userIds");
        }
        if (project.status() == ProjectStatus.REMOVED
                || repository.lockEligible(projectId, userIds) != userIds.size()) {
            throw new BusinessRuleException("MSG06", Map.of());
        }
        try {
            repository.addMembers(projectId, userIds);
        } catch (DuplicateKeyException concurrentAdd) {
            // Design E5: another request added one of these people after our eligibility check.
            throw new BusinessRuleException("MSG06", Map.of());
        }
        audit.record(AuditAction.PROJECT_MEMBER_CHANGE, "PROJECT", projectId, Map.of("added", userIds));
        return repository.members(projectId);
    }

    @Override
    @Transactional
    public List<ProjectMemberResponse> removeMember(UUID projectId, UUID userId) {
        ProjectSummaryResponse project = requireManagedProject(projectId);
        // Design D14: one delete covers "not a member", "already removed" and "the Project Manager's row".
        if (project.status() == ProjectStatus.REMOVED || repository.removeMember(projectId, userId) == 0) {
            throw new BusinessRuleException("MSG06", Map.of());
        }
        audit.record(AuditAction.PROJECT_MEMBER_CHANGE, "PROJECT", projectId, Map.of("removed", List.of(userId)));
        return repository.members(projectId);
    }

    // Design D4: an unknown Project is 404 before the manager check answers 403.
    private ProjectSummaryResponse requireManagedProject(UUID projectId) {
        scope.currentWorkspaceId(); // throws when no Workspace is bound, before any read
        ProjectSummaryResponse project =
                repository.findProject(projectId).orElseThrow(() -> new NotFoundException("MSG04", Map.of()));
        UUID caller = TSolveUser.current()
                .map(TSolveUser::id)
                .orElseThrow(() -> new AccessDeniedException("No signed-in user"));
        if (!repository.isManager(caller, projectId)) {
            throw new AccessDeniedException("Not the Project Manager of this Project");
        }
        return project;
    }
}
