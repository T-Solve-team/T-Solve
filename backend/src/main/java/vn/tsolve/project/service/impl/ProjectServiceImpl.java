package vn.tsolve.project.service.impl;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.tsolve.common.exception.BusinessRuleException;
import vn.tsolve.common.security.TSolveUser;
import vn.tsolve.identity.signin.port.TenantScope;
import vn.tsolve.project.dto.ProjectConnectionTestRequest;
import vn.tsolve.project.dto.ProjectConnectionTestResponse;
import vn.tsolve.project.entity.Project;
import vn.tsolve.project.entity.ProjectStatus;
import vn.tsolve.project.exception.ProjectAccessDeniedException;
import vn.tsolve.project.exception.ProjectTrackerUnavailableException;
import vn.tsolve.project.repository.ProjectLinkRepository;
import vn.tsolve.project.repository.ProjectLinkRepository.CallerDepartment;
import vn.tsolve.project.repository.ProjectLinkRepository.Link;
import vn.tsolve.project.repository.ProjectRepository;
import vn.tsolve.project.service.ProjectService;
import vn.tsolve.tracker.dto.TrackerProjectLookup.Outcome;
import vn.tsolve.tracker.dto.TrackerStatus;
import vn.tsolve.tracker.service.TrackerProjectService;

@Service
public class ProjectServiceImpl implements ProjectService {

    private static final Pattern KEY_FORMAT = Pattern.compile("[A-Z][A-Z0-9_]*");
    private static final String MSG48 = "MSG48";
    private static final String TRACKER_NAME = "Jira";

    private final ProjectRepository projectRepository;
    private final ProjectLinkRepository links;
    private final TrackerProjectService tracker;
    private final TenantScope scope;

    public ProjectServiceImpl(
            ProjectRepository projectRepository,
            ProjectLinkRepository links,
            TrackerProjectService tracker,
            TenantScope scope) {
        this.projectRepository = projectRepository;
        this.links = links;
        this.tracker = tracker;
        this.scope = scope;
    }

    @Override
    public ProjectConnectionTestResponse testConnection(ProjectConnectionTestRequest request) {
        scope.currentWorkspaceId(); // throws when no Workspace is bound: 500 MSG06 before any read (AC-007.5)
        UUID caller = TSolveUser.current()
                .orElseThrow(ProjectAccessDeniedException::new)
                .id();
        CallerDepartment department = links.findCallerDepartment(caller).orElseThrow(ProjectAccessDeniedException::new);
        String key = request.jiraProjectKey().strip().toUpperCase(Locale.ROOT);
        if (!KEY_FORMAT.matcher(key).matches()) {
            throw new BusinessRuleException(MSG48, Map.of("tracker_name", TRACKER_NAME));
        }

        Optional<Link> requested = links.findLink(key);
        requested.ifPresent(link -> refuseIfLinked(link, department, caller));
        var jira = tracker.findProject(key);
        if (jira.outcome() == Outcome.UNAVAILABLE) {
            throw new ProjectTrackerUnavailableException();
        }
        if (jira.outcome() != Outcome.FOUND) {
            throw new BusinessRuleException(MSG48, Map.of("tracker_name", TRACKER_NAME));
        }

        Optional<Link> linked = requested;
        if (!key.equals(jira.key())) {
            Optional<Link> canonical = links.findLink(jira.key());
            canonical.ifPresent(link -> {
                refuseIfLinked(link, department, caller);
                if (requested.isPresent() && !requested.get().projectId().equals(link.projectId())) {
                    throw conflict(link.departmentName());
                }
            });
            linked = canonical.or(() -> requested);
        }

        List<String> doneIds = jira.statuses().stream()
                .filter(TrackerStatus::done)
                .map(TrackerStatus::id)
                .toList();
        var selected = new LinkedHashSet<>(linked.map(Link::doneStatusIds).orElse(List.of()));
        selected.retainAll(jira.statuses().stream().map(TrackerStatus::id).toList());
        return new ProjectConnectionTestResponse(
                jira.key(),
                jira.statuses(),
                linked.isPresent(),
                linked.map(Link::projectId).orElse(null),
                selected.isEmpty() ? doneIds : List.copyOf(selected),
                linked.map(Link::reviewRange).orElse(3));
    }

    private void refuseIfLinked(Link link, CallerDepartment department, UUID caller) {
        if (!"REMOVED".equals(link.status())
                || link.departmentId() != department.departmentId()
                || (link.managerProtected() && !caller.equals(link.managerId()))) {
            throw conflict(link.departmentName());
        }
    }

    private BusinessRuleException conflict(String departmentName) {
        return new BusinessRuleException(
                "MSG49", Map.of("tracker_name", TRACKER_NAME, "department_name", departmentName), "jiraProjectKey");
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<UUID> findActivePublicIdByJiraProjectKey(String jiraProjectKey) {
        return projectRepository
                .findByJiraProjectKeyAndStatus(jiraProjectKey, ProjectStatus.ACTIVE)
                .map(Project::getPublicId);
    }
}
