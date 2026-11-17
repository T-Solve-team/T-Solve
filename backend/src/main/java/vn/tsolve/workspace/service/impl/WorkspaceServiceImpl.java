package vn.tsolve.workspace.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceException;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.hibernate.exception.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
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
import vn.tsolve.identity.signin.port.TenantScope;
import vn.tsolve.workspace.dto.JiraAppToken;
import vn.tsolve.workspace.dto.WorkspaceInfoMapper;
import vn.tsolve.workspace.dto.WorkspaceInfoResponse;
import vn.tsolve.workspace.entity.WorkspaceInfo;
import vn.tsolve.workspace.repository.WorkspaceInfoRepository;
import vn.tsolve.workspace.service.JiraAppTokenService;
import vn.tsolve.workspace.service.WorkspaceService;
import vn.tsolve.workspace.site.JiraSiteLinkRefusedException;
import vn.tsolve.workspace.site.JiraSiteLinker;
import vn.tsolve.workspace.site.JiraSiteRule;

@Service
public class WorkspaceServiceImpl implements WorkspaceService {
    private static final Logger log = LoggerFactory.getLogger(WorkspaceServiceImpl.class);
    private final TenantScope scope;
    private final WorkspaceInfoRepository repository;
    private final EntityManager entities;
    private final JiraSiteLinker linker;
    private final JiraAppTokenService tokens;
    private final AuditService audit;
    private final WorkspaceInfoMapper mapper;
    private final TransactionTemplate transaction;

    public WorkspaceServiceImpl(
            TenantScope scope,
            WorkspaceInfoRepository repository,
            EntityManager entities,
            JiraSiteLinker linker,
            JiraAppTokenService tokens,
            AuditService audit,
            WorkspaceInfoMapper mapper,
            PlatformTransactionManager transactions) {
        this.scope = scope;
        this.repository = repository;
        this.entities = entities;
        this.linker = linker;
        this.tokens = tokens;
        this.audit = audit;
        this.mapper = mapper;
        this.transaction = new TransactionTemplate(transactions);
    }

    @Override
    public WorkspaceInfoResponse setup(JsonNode input) {
        if (input == null || !input.isObject()) {
            throw error(null, "MSG03", Map.of());
        }
        // AC-005.15: serviceAccountEmail and serviceAccountToken are ignored (KAN-349: no customer credential).
        String companyName = field(input, "companyName", "Company name", 100).strip();
        String rawSite = field(input, "jiraSiteUrl", "Jira site address", 200);
        String site = JiraSiteRule.normalise(rawSite).orElseThrow(() -> error("jiraSiteUrl", "MSG61", Map.of()));
        UUID workspaceId = scope.currentWorkspaceId();
        if (repository.count() != 0) {
            throw alreadySetUp();
        }
        String cloudId;
        try {
            // AC-005.6, AC-005.14: the site's cloud id, read with no transaction open (D30).
            cloudId = linker.readCloudId(site);
        } catch (JiraSiteLinkRefusedException failure) {
            log.warn("Jira tenant information could not be read during Workspace setup");
            throw error("jiraSiteUrl", "MSG61", Map.of());
        }
        // Link (AC-005.8), insert and audit commit together.
        return transaction.execute(status -> {
            try {
                linker.linkJiraSite(site);
            } catch (JiraSiteLinkRefusedException failure) {
                if (failure.getReason() == JiraSiteLinkRefusedException.Reason.LINKED_ELSEWHERE) {
                    throw error("jiraSiteUrl", "MSG153", Map.of("tracker_name", "Jira"));
                }
                log.warn("Jira tenant information could not be read during Workspace setup");
                throw error("jiraSiteUrl", "MSG61", Map.of());
            }
            WorkspaceInfo workspace = new WorkspaceInfo(workspaceId, companyName, site, cloudId);
            try {
                // Assigned ids make JpaRepository.save merge: a concurrent Setup could overwrite the winner.
                // Persist is insert-only; flush brings the unique violation inside this error boundary.
                entities.persist(workspace);
                entities.flush();
            } catch (PersistenceException | DataIntegrityViolationException failure) {
                if (isConcurrentSetup(failure)) {
                    throw alreadySetUp();
                }
                throw failure;
            }
            audit.record(
                    AuditAction.TRACKER_CONFIGURATION_CHANGE,
                    "WORKSPACE",
                    workspaceId,
                    Map.of("companyName", companyName, "jiraSiteUrl", site));
            return response(workspace);
        });
    }

    @Override
    @Transactional(readOnly = true)
    public WorkspaceInfoResponse get() {
        return response(repository
                .findById(scope.currentWorkspaceId())
                .orElseThrow(() -> new NotFoundException("MSG60", Map.of())));
    }

    @Override
    public WorkspaceInfoResponse update(JsonNode input) {
        if (input == null || !input.isObject()) {
            throw error(null, "MSG03", Map.of());
        }
        // AC-007.7: jiraSiteUrl, serviceAccountEmail and serviceAccountToken are ignored; AC-007.6: no Jira call.
        String companyName = field(input, "companyName", "Company name", 100).strip();
        UUID workspaceId = scope.currentWorkspaceId();
        WorkspaceInfo stored =
                repository.findById(workspaceId).orElseThrow(() -> new NotFoundException("MSG60", Map.of()));
        String oldCompanyName = stored.getCompanyName();
        stored.rename(companyName);
        return transaction.execute(status -> {
            WorkspaceInfo saved = repository.save(stored);
            audit.record(
                    AuditAction.TRACKER_CONFIGURATION_CHANGE,
                    "WORKSPACE",
                    workspaceId,
                    Map.of("companyName", Map.of("old", oldCompanyName, "new", companyName)));
            return response(saved);
        });
    }

    // AC-006.6: the Jira App status comes with every answer.
    private WorkspaceInfoResponse response(WorkspaceInfo workspace) {
        Optional<JiraAppToken> token = tokens.current(workspace.getId());
        return mapper.toResponse(
                workspace,
                token.isPresent(),
                token.map(JiraAppToken::receivedAt).orElse(null));
    }

    private static String field(JsonNode input, String field, String label, Integer maxLength) {
        JsonNode value = input.path(field);
        if (!value.isTextual() || value.textValue().isBlank()) {
            throw error(field, "MSG01", Map.of("field_name", label));
        }
        String text = value.textValue();
        if (maxLength != null && text.length() > maxLength) {
            throw error(field, "MSG02", Map.of("field_name", label, "max_length", maxLength));
        }
        return text;
    }

    private static boolean isConcurrentSetup(Throwable failure) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof ConstraintViolationException constraint
                    && "23505".equals(constraint.getSQLState())
                    && ("uq_workspace_singleton".equals(constraint.getConstraintName())
                            || "pk_workspace_info".equals(constraint.getConstraintName()))) {
                return true;
            }
        }
        return false;
    }

    private static BusinessRuleException alreadySetUp() {
        return new BusinessRuleException("WORKSPACE_ALREADY_SET_UP", Map.of());
    }

    private static FieldMessageCodeException error(String field, String code, Map<String, Object> params) {
        return new FieldMessageCodeException(HttpStatus.BAD_REQUEST, code, params, field);
    }
}
