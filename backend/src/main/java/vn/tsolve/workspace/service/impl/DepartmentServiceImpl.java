package vn.tsolve.workspace.service.impl;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import vn.tsolve.common.audit.AuditAction;
import vn.tsolve.common.audit.AuditService;
import vn.tsolve.common.exception.BusinessRuleException;
import vn.tsolve.common.exception.FieldMessageCodeException;
import vn.tsolve.common.exception.NotFoundException;
import vn.tsolve.identity.dto.DepartmentRefResponse;
import vn.tsolve.identity.signin.port.TenantScope;
import vn.tsolve.tracker.service.TrackerProjectService;
import vn.tsolve.workspace.dto.CreateDepartmentRequest;
import vn.tsolve.workspace.dto.DepartmentDetailResponse;
import vn.tsolve.workspace.dto.DepartmentProjectOptionResponse;
import vn.tsolve.workspace.dto.UpdateDepartmentRequest;
import vn.tsolve.workspace.repository.DepartmentRepository;
import vn.tsolve.workspace.repository.DepartmentRepository.Current;
import vn.tsolve.workspace.repository.DepartmentRepository.ProjectOption;
import vn.tsolve.workspace.service.DepartmentService;

@Service
public class DepartmentServiceImpl implements DepartmentService {
    private final DepartmentRepository repository;
    private final AuditService audit;
    private final TenantScope scope;
    private final TrackerProjectService tracker;
    private final TransactionTemplate readOnly;
    private final TransactionTemplate transaction;

    public DepartmentServiceImpl(
            DepartmentRepository repository,
            AuditService audit,
            TenantScope scope,
            TrackerProjectService tracker,
            PlatformTransactionManager transactions) {
        this.repository = repository;
        this.audit = audit;
        this.scope = scope;
        this.tracker = tracker;
        this.readOnly = new TransactionTemplate(transactions);
        this.readOnly.setReadOnly(true);
        this.transaction = new TransactionTemplate(transactions);
    }

    @Override
    @Transactional
    public DepartmentRefResponse create(CreateDepartmentRequest request) {
        scope.currentWorkspaceId(); // AC-001.15: throws when no Workspace is bound, before any write
        String name = request.name().strip();
        DepartmentRepository.Created department = repository
                .insertDepartment(name)
                .orElseThrow(() -> new BusinessRuleException("MSG43", Map.of(), "name"));
        UUID spaceId = repository.insertSpace(department.id(), name);
        audit.record(
                AuditAction.DEPARTMENT_CHANGE,
                "DEPARTMENT",
                department.publicId(),
                Map.of("operation", "CREATE", "name", name, "spaceId", spaceId));
        return new DepartmentRefResponse(department.publicId(), name);
    }

    @Override
    @Transactional(readOnly = true)
    public DepartmentDetailResponse get(UUID departmentId) {
        scope.currentWorkspaceId(); // AC-003.21
        Current current = repository.findActive(departmentId).orElseThrow(DepartmentServiceImpl::notFound);
        DepartmentProjectOptionResponse defaultProject = current.defaultProjectId() == null
                ? null
                : new DepartmentProjectOptionResponse(current.defaultProjectId(), current.defaultProjectKey());
        return detail(departmentId, current.name(), defaultProject, current.id());
    }

    // No @Transactional: the Jira check runs between a read-only pre-read and the write transaction (NF 2).
    @Override
    public DepartmentDetailResponse update(UUID departmentId, UpdateDepartmentRequest request) {
        scope.currentWorkspaceId(); // AC-003.21, AC-012.14: throws before any repository or Jira call
        String name = request.name().strip();
        UUID wanted = request.defaultProjectId();
        ProjectOption toCheck = readOnly.execute(status -> {
            Current before = repository.findActive(departmentId).orElseThrow(DepartmentServiceImpl::notFound);
            if (wanted == null || wanted.equals(before.defaultProjectId())) {
                return null; // AC-012.10: no Jira call
            }
            return repository
                    .findActiveProject(before.id(), wanted)
                    .orElseThrow(() -> projectError("MSG04", Map.of())); // AC-012.5
        });
        if (toCheck != null) {
            switch (tracker.checkChatbotProject(toCheck.jiraProjectKey())) { // AC-012.7
                case READY -> {}
                case NOT_READY -> throw projectError("MSG157", Map.of("project_name", toCheck.jiraProjectKey()));
                case DENIED, UNAVAILABLE -> throw projectError("MSG156", Map.of("tracker_name", "Jira"));
            }
        }
        return transaction.execute(status -> save(departmentId, name, wanted));
    }

    private DepartmentDetailResponse save(UUID departmentId, String name, UUID wanted) {
        Current current = repository.lockActive(departmentId).orElseThrow(DepartmentServiceImpl::notFound);
        // A pre-read default changed by a concurrent save is stored without a new check: it passed when chosen.
        ProjectOption chosen = wanted == null
                ? null
                : repository
                        .lockActiveProject(current.id(), wanted)
                        .orElseThrow(() -> projectError("MSG04", Map.of())); // AC-012.13
        DepartmentProjectOptionResponse after = option(chosen);
        boolean nameChanged = !name.equals(current.name());
        boolean defaultChanged = !Objects.equals(wanted, current.defaultProjectId());
        if (!nameChanged && !defaultChanged) {
            return detail(departmentId, current.name(), after, current.id()); // AC-003.14
        }
        // LinkedHashMap: a cleared default Project is a null value, which Map.of refuses (AC-012.11).
        Map<String, Object> beforeDetails = new LinkedHashMap<>();
        Map<String, Object> afterDetails = new LinkedHashMap<>();
        beforeDetails.put("name", current.name());
        afterDetails.put("name", name);
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("operation", "UPDATE");
        details.put("before", beforeDetails);
        details.put("after", afterDetails);
        if (nameChanged) {
            repository.renameDepartment(current.id(), name); // 23505: MSG43 (AC-012.15)
            details.put("spaceId", repository.renameSpace(current.id(), name));
        }
        if (defaultChanged) {
            repository.setDefaultProject(current.id(), chosen == null ? null : chosen.rowId()); // AC-012.4, AC-012.6
            beforeDetails.put("defaultProject", auditOption(current.defaultProjectId(), current.defaultProjectKey()));
            afterDetails.put("defaultProject", chosen == null ? null : auditOption(wanted, chosen.jiraProjectKey()));
        }
        audit.record(AuditAction.DEPARTMENT_CHANGE, "DEPARTMENT", departmentId, details);
        return detail(departmentId, name, after, current.id());
    }

    private DepartmentDetailResponse detail(
            UUID departmentId, String name, DepartmentProjectOptionResponse defaultProject, long rowId) {
        List<DepartmentProjectOptionResponse> projects = repository.activeProjects(rowId).stream()
                .map(DepartmentServiceImpl::option)
                .toList();
        return new DepartmentDetailResponse(departmentId, name, defaultProject, projects);
    }

    private static DepartmentProjectOptionResponse option(ProjectOption project) {
        return project == null
                ? null
                : new DepartmentProjectOptionResponse(project.projectId(), project.jiraProjectKey());
    }

    private static Map<String, Object> auditOption(UUID projectId, String jiraProjectKey) {
        return projectId == null ? null : Map.of("projectId", projectId, "jiraProjectKey", jiraProjectKey);
    }

    private static NotFoundException notFound() {
        return new NotFoundException("MSG04", Map.of());
    }

    private static FieldMessageCodeException projectError(String code, Map<String, Object> params) {
        return new FieldMessageCodeException(HttpStatus.BAD_REQUEST, code, params, "defaultProjectId");
    }
}
