package vn.tsolve.workspace.service.impl;

import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.tsolve.common.audit.AuditAction;
import vn.tsolve.common.audit.AuditService;
import vn.tsolve.common.exception.BusinessRuleException;
import vn.tsolve.identity.dto.DepartmentRefResponse;
import vn.tsolve.identity.signin.port.TenantScope;
import vn.tsolve.workspace.dto.CreateDepartmentRequest;
import vn.tsolve.workspace.repository.DepartmentRepository;
import vn.tsolve.workspace.service.DepartmentService;

@Service
public class DepartmentServiceImpl implements DepartmentService {
    private final DepartmentRepository repository;
    private final AuditService audit;
    private final TenantScope scope;

    public DepartmentServiceImpl(DepartmentRepository repository, AuditService audit, TenantScope scope) {
        this.repository = repository;
        this.audit = audit;
        this.scope = scope;
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
}
