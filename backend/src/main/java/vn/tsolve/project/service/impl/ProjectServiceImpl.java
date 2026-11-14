package vn.tsolve.project.service.impl;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.regex.Pattern;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import vn.tsolve.common.audit.AuditAction;
import vn.tsolve.common.audit.AuditService;
import vn.tsolve.common.exception.BusinessRuleException;
import vn.tsolve.common.exception.FieldMessageCodeException;
import vn.tsolve.common.security.TSolveUser;
import vn.tsolve.identity.dto.DepartmentRefResponse;
import vn.tsolve.identity.signin.port.TenantScope;
import vn.tsolve.project.dto.CreateProjectRequest;
import vn.tsolve.project.dto.ProjectConnectionTestRequest;
import vn.tsolve.project.dto.ProjectConnectionTestResponse;
import vn.tsolve.project.dto.ProjectListResponse;
import vn.tsolve.project.dto.ProjectResponse;
import vn.tsolve.project.dto.ProjectSaveResult;
import vn.tsolve.project.entity.Project;
import vn.tsolve.project.entity.ProjectStatus;
import vn.tsolve.project.exception.ProjectAccessDeniedException;
import vn.tsolve.project.exception.ProjectTrackerUnavailableException;
import vn.tsolve.project.repository.ProjectLinkRepository;
import vn.tsolve.project.repository.ProjectLinkRepository.CallerDepartment;
import vn.tsolve.project.repository.ProjectLinkRepository.Link;
import vn.tsolve.project.repository.ProjectRepository;
import vn.tsolve.project.service.ProjectService;
import vn.tsolve.tracker.dto.TrackerProjectLookup;
import vn.tsolve.tracker.dto.TrackerProjectLookup.Outcome;
import vn.tsolve.tracker.dto.TrackerStatus;
import vn.tsolve.tracker.service.TrackerProjectService;

@Service
public class ProjectServiceImpl implements ProjectService {

    private static final Pattern KEY_FORMAT = Pattern.compile("[A-Z][A-Z0-9_]*");
    private static final BigDecimal MAX_REVIEW_RANGE = BigDecimal.valueOf(Integer.MAX_VALUE);
    private static final String MSG48 = "MSG48";
    private static final String TRACKER_NAME = "Jira";
    private static final String DONE_STATUS_IDS = "doneStatusIds";
    private static final String REVIEW_RANGE = "reviewRange";

    private final ProjectRepository projectRepository;
    private final ProjectLinkRepository links;
    private final TrackerProjectService tracker;
    private final TenantScope scope;
    private final AuditService audit;
    private final TransactionTemplate writeTransaction;

    public ProjectServiceImpl(
            ProjectRepository projectRepository,
            ProjectLinkRepository links,
            TrackerProjectService tracker,
            TenantScope scope,
            AuditService audit,
            PlatformTransactionManager transactionManager) {
        this.projectRepository = projectRepository;
        this.links = links;
        this.tracker = tracker;
        this.scope = scope;
        this.audit = audit;
        this.writeTransaction = new TransactionTemplate(transactionManager);
        // Pinned, not inherited: the lost insert's re-read must see the winner committed after ON CONFLICT waited, and
        // a reconnect must re-read the row after its lock. Under REPEATABLE READ both fail as 500 instead of MSG49.
        this.writeTransaction.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
    }

    /** The caller, their own eligible Department and the normalised key, checked in the AC-007.8 order. */
    private record CheckedKey(UUID caller, CallerDepartment department, String key) {}

    private record CallerScope(UUID caller, CallerDepartment department) {}

    @Override
    public ProjectConnectionTestResponse testConnection(ProjectConnectionTestRequest request) {
        CheckedKey checked = checkScopeAndKey(request.jiraProjectKey());
        UUID caller = checked.caller();
        CallerDepartment department = checked.department();
        String key = checked.key();

        Optional<Link> requested = links.findLink(key);
        requested.ifPresent(link -> refuseIfLinked(link, department, caller));
        TrackerProjectLookup jira = readJiraProject(key);
        Optional<Link> linked = checkCanonicalLink(key, jira.key(), requested, links::findLink, department, caller);

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

    @Override
    public ProjectSaveResult create(CreateProjectRequest request) {
        CheckedKey checked = checkScopeAndKey(request.jiraProjectKey());
        UUID caller = checked.caller();
        String key = checked.key();
        int range = requireWholeRange(request.reviewRange());
        List<String> ids = requireSelectedIds(request.doneStatusIds());

        Optional<Link> requested = links.findLink(key);
        requested.ifPresent(link -> refuseIfLinked(link, checked.department(), caller));
        TrackerProjectLookup jira = readJiraProject(key); // always a fresh read, outside any transaction (AC-002.18)
        checkCanonicalLink(key, jira.key(), requested, links::findLink, checked.department(), caller);
        List<String> available = jira.statuses().stream().map(TrackerStatus::id).toList();
        if (!available.containsAll(ids)) {
            throw required(DONE_STATUS_IDS);
        }

        try {
            return writeTransaction.execute(tx -> {
                CallerDepartment department =
                        links.findCallerDepartment(caller).orElseThrow(ProjectAccessDeniedException::new);
                Optional<Link> link = recheckLinks(key, jira.key(), department, caller);
                UUID id = link.isPresent()
                        ? reconnect(link.get(), caller, jira.key(), ids, range)
                        : insertNew(department, caller, jira.key(), ids, range);
                ProjectResponse saved = links.readProject(id).orElseThrow();
                String operation = link.isPresent() ? "RECONNECT" : "CREATE";
                audit.record(AuditAction.TRACKER_CONFIGURATION_CHANGE, "PROJECT", id, auditDetails(operation, saved));
                return new ProjectSaveResult(saved, link.isEmpty());
            });
        } catch (DuplicateKeyException error) {
            // Only a reconnect's canonical-key update can meet a concurrent insert of that key; after the rollback
            // the committed winner is named (D41). Any other constraint stays a failure.
            if (!String.valueOf(error.getMostSpecificCause().getMessage()).contains("uq_project_jira_key")) {
                throw error;
            }
            throw lostInsert(jira.key());
        }
    }

    private UUID insertNew(CallerDepartment department, UUID caller, String canonicalKey, List<String> ids, int range) {
        UUID id = links.insertProject(department.departmentId(), canonicalKey, ids, range)
                .orElseThrow(() -> lostInsert(canonicalKey));
        links.upsertManager(caller, id);
        return id;
    }

    /** The locked, re-read Removed row becomes Active in place; memberships change only for a new manager. */
    private UUID reconnect(Link link, UUID caller, String canonicalKey, List<String> ids, int range) {
        UUID id = link.projectId();
        if (links.reconnectProject(id, canonicalKey, ids, range) == 0) {
            throw conflict(link.departmentName());
        }
        if (!caller.equals(link.managerId())) {
            if (link.managerId() != null) {
                links.deleteManager(id, link.managerId());
            }
            links.upsertManager(caller, id);
        }
        return id;
    }

    private CheckedKey checkScopeAndKey(String requestedKey) {
        CallerScope checked = checkScope();
        UUID caller = checked.caller();
        CallerDepartment department = checked.department();
        String key = requestedKey.strip().toUpperCase(Locale.ROOT);
        if (!KEY_FORMAT.matcher(key).matches()) {
            throw new BusinessRuleException(MSG48, Map.of("tracker_name", TRACKER_NAME));
        }
        return new CheckedKey(caller, department, key);
    }

    /** The bound Workspace, then the caller's own eligible Department, both from the server side (AC-007.7). */
    private CallerScope checkScope() {
        scope.currentWorkspaceId(); // throws when no Workspace is bound: 500 MSG06 before any read (AC-007.5)
        UUID caller = TSolveUser.current()
                .orElseThrow(ProjectAccessDeniedException::new)
                .id();
        CallerDepartment department = links.findCallerDepartment(caller).orElseThrow(ProjectAccessDeniedException::new);
        return new CallerScope(caller, department);
    }

    private TrackerProjectLookup readJiraProject(String key) {
        TrackerProjectLookup jira = tracker.findProject(key);
        if (jira.outcome() == Outcome.UNAVAILABLE) {
            throw new ProjectTrackerUnavailableException();
        }
        if (jira.outcome() != Outcome.FOUND) {
            throw new BusinessRuleException(MSG48, Map.of("tracker_name", TRACKER_NAME));
        }
        return jira;
    }

    /** The one Project the requested and canonical keys resolve to, if allowed (AC-001.19, AC-001.21, AC-001.22). */
    private Optional<Link> checkCanonicalLink(
            String key,
            String canonicalKey,
            Optional<Link> requested,
            Function<String, Optional<Link>> find,
            CallerDepartment department,
            UUID caller) {
        if (key.equals(canonicalKey)) {
            return requested;
        }
        Optional<Link> canonical = find.apply(canonicalKey);
        canonical.ifPresent(link -> {
            refuseIfLinked(link, department, caller);
            if (requested.isPresent() && !requested.get().projectId().equals(link.projectId())) {
                throw conflict(link.departmentName());
            }
        });
        return canonical.or(() -> requested);
    }

    /** Inside the write transaction: lock and re-read both links, then apply the same rules (AC-002.17). */
    private Optional<Link> recheckLinks(String key, String canonicalKey, CallerDepartment department, UUID caller) {
        Optional<Link> requested = lockedLink(key);
        requested.ifPresent(link -> refuseIfLinked(link, department, caller));
        return checkCanonicalLink(key, canonicalKey, requested, this::lockedLink, department, caller);
    }

    // FOR UPDATE does not re-read the joined manager rows, so the link is read again once the row is locked.
    private Optional<Link> lockedLink(String key) {
        Optional<Link> found = links.findLink(key);
        if (found.isEmpty()) {
            return found;
        }
        links.lockProject(found.get().projectId());
        return links.findLink(key);
    }

    private BusinessRuleException lostInsert(String canonicalKey) {
        return links.findLink(canonicalKey)
                .map(winner -> conflict(winner.departmentName()))
                .orElseThrow(() -> new IllegalStateException("Project insert skipped without a conflicting row"));
    }

    private static int requireWholeRange(BigDecimal range) {
        if (range == null) {
            throw required(REVIEW_RANGE);
        }
        if (range.stripTrailingZeros().scale() > 0
                || range.compareTo(BigDecimal.ONE) < 0
                || range.compareTo(MAX_REVIEW_RANGE) > 0) {
            throw new FieldMessageCodeException(HttpStatus.BAD_REQUEST, "MSG44", Map.of(), REVIEW_RANGE);
        }
        return range.intValueExact();
    }

    /** Exact ids, never trimmed; duplicates collapse in first-seen order (AC-002.15). */
    private static List<String> requireSelectedIds(List<String> ids) {
        if (ids == null || ids.isEmpty() || ids.stream().anyMatch(id -> id == null || id.isBlank())) {
            throw required(DONE_STATUS_IDS);
        }
        return List.copyOf(new LinkedHashSet<>(ids));
    }

    private static FieldMessageCodeException required(String field) {
        return new FieldMessageCodeException(HttpStatus.BAD_REQUEST, "MSG01", Map.of("field_name", field), field);
    }

    private static Map<String, Object> auditDetails(String operation, ProjectResponse saved) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("operation", operation);
        details.put("jiraProjectKey", saved.jiraProjectKey());
        details.put("departmentId", saved.departmentId());
        details.put(DONE_STATUS_IDS, saved.doneStatusIds());
        details.put(REVIEW_RANGE, saved.reviewRange());
        return details;
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

    // Like testConnection, no transaction: two independent reads, and READ COMMITTED would not join their snapshots.
    @Override
    public ProjectListResponse list() {
        CallerDepartment department = checkScope().department();
        return new ProjectListResponse(
                new DepartmentRefResponse(department.departmentPublicId(), department.name()),
                links.listProjects(department.departmentId()));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<UUID> findActivePublicIdByJiraProjectKey(String jiraProjectKey) {
        return projectRepository
                .findByJiraProjectKeyAndStatus(jiraProjectKey, ProjectStatus.ACTIVE)
                .map(Project::getPublicId);
    }
}
