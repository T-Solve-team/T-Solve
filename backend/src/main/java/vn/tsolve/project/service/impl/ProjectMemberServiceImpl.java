package vn.tsolve.project.service.impl;

import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
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
import vn.tsolve.identity.service.JiraAccountLinkLookup;
import vn.tsolve.identity.signin.port.TenantScope;
import vn.tsolve.project.dto.AddProjectMembersRequest;
import vn.tsolve.project.dto.JiraProjectRoleResponse;
import vn.tsolve.project.dto.JiraRolePeopleResponse;
import vn.tsolve.project.dto.JiraRolePersonResponse;
import vn.tsolve.project.dto.JiraRolePersonResponse.Status;
import vn.tsolve.project.dto.MemberCandidateResponse;
import vn.tsolve.project.dto.ProjectMemberResponse;
import vn.tsolve.project.dto.ProjectSummaryResponse;
import vn.tsolve.project.entity.ProjectStatus;
import vn.tsolve.project.exception.ProjectTrackerUnavailableException;
import vn.tsolve.project.repository.ProjectMemberRepository;
import vn.tsolve.project.service.ProjectMemberService;
import vn.tsolve.tracker.dto.TrackerRoleActors;
import vn.tsolve.tracker.service.TrackerProjectService;

@Service
public class ProjectMemberServiceImpl implements ProjectMemberService {
    static final int MAX_QUERY_LENGTH = 100;

    // The role Jira gives to apps; it is never a team (AC-011.1).
    static final String APP_ROLE = "atlassian-addons-project-access";

    private final ProjectMemberRepository repository;
    private final AuditService audit;
    private final TenantScope scope;
    private final TrackerProjectService tracker;
    private final JiraAccountLinkLookup links;

    public ProjectMemberServiceImpl(
            ProjectMemberRepository repository,
            AuditService audit,
            TenantScope scope,
            TrackerProjectService tracker,
            JiraAccountLinkLookup links) {
        this.repository = repository;
        this.audit = audit;
        this.scope = scope;
        this.tracker = tracker;
        this.links = links;
    }

    // No transaction: the Jira read runs between the refusal checks and the local reads (AGENTS.md §4).
    @Override
    public List<JiraProjectRoleResponse> jiraRoles(UUID projectId) {
        String key = requireManagedProject(projectId).jiraProjectKey();
        return tracker.projectRoles(key).orElseThrow(ProjectTrackerUnavailableException::new).stream()
                .filter(role -> !APP_ROLE.equals(role.name()))
                .sorted(Comparator.comparing(role -> role.name().toLowerCase(Locale.ROOT)))
                .map(role -> new JiraProjectRoleResponse(role.id(), role.name()))
                .toList();
    }

    @Override
    public JiraRolePeopleResponse jiraRolePeople(UUID projectId, long roleId) {
        String key = requireManagedProject(projectId).jiraProjectKey();
        TrackerRoleActors actors = tracker.roleActors(key, roleId).orElseThrow(ProjectTrackerUnavailableException::new);
        Map<String, TrackerRoleActors.Person> people = new LinkedHashMap<>();
        actors.people().forEach(person -> people.putIfAbsent(person.accountId(), person));
        Map<String, UUID> linked = links.linkedAccountIds(people.keySet());
        Map<UUID, ProjectMemberRepository.MemberState> states =
                repository.memberStates(projectId, Set.copyOf(linked.values()));
        List<JiraRolePersonResponse> listed = people.values().stream()
                .sorted(Comparator.comparing(person -> person.displayName().toLowerCase(Locale.ROOT)))
                .map(person -> {
                    UUID account = linked.get(person.accountId());
                    return classify(person, account == null ? null : states.get(account));
                })
                .toList();
        return new JiraRolePeopleResponse(listed, actors.groups());
    }

    // Order of the reasons: not linked, already a member, not Active, not a Ticket Resolver (design KAN-367).
    private static JiraRolePersonResponse classify(
            TrackerRoleActors.Person person, ProjectMemberRepository.MemberState state) {
        Status status;
        if (state == null) {
            status = Status.NOT_LINKED;
        } else if (state.member()) {
            status = Status.ALREADY_MEMBER;
        } else if (!state.active()) {
            status = Status.NOT_ACTIVE;
        } else if (!state.ticketResolver()) {
            status = Status.NOT_TICKET_RESOLVER;
        } else {
            return JiraRolePersonResponse.addable(
                    person.accountId(), person.displayName(), state.id(), state.fullName(), state.email());
        }
        return JiraRolePersonResponse.notAddable(person.accountId(), person.displayName(), status);
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
        if (project.status() == ProjectStatus.REMOVED) {
            throw new BusinessRuleException("MSG06", Map.of());
        }
        repository.lockProject(projectId);
        int eligible = repository.lockEligible(projectId, userIds);
        // The reads above ran before the locks; re-read status and manager now that the waits are over.
        if (requireManagedProject(projectId).status() == ProjectStatus.REMOVED || eligible != userIds.size()) {
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
        if (project.status() == ProjectStatus.REMOVED) {
            throw new BusinessRuleException("MSG06", Map.of());
        }
        repository.lockProject(projectId);
        repository.lockAccounts(List.of(userId));
        // Re-read status and manager now that the lock waits are over.
        if (requireManagedProject(projectId).status() == ProjectStatus.REMOVED
                || repository.removeMember(projectId, userId) == 0) {
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
