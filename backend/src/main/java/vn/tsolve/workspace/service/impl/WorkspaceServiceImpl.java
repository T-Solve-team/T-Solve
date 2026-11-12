package vn.tsolve.workspace.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceException;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
import org.hibernate.exception.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.tsolve.common.audit.AuditAction;
import vn.tsolve.common.audit.AuditService;
import vn.tsolve.common.exception.BusinessRuleException;
import vn.tsolve.common.exception.FieldMessageCodeException;
import vn.tsolve.common.exception.NotFoundException;
import vn.tsolve.identity.signin.port.TenantScope;
import vn.tsolve.tracker.client.TicketTrackerClient;
import vn.tsolve.tracker.dto.JiraAccount;
import vn.tsolve.tracker.exception.JiraRejectedException;
import vn.tsolve.tracker.exception.JiraUnavailableException;
import vn.tsolve.workspace.crypto.TokenCipher;
import vn.tsolve.workspace.dto.WorkspaceInfoMapper;
import vn.tsolve.workspace.dto.WorkspaceInfoResponse;
import vn.tsolve.workspace.entity.WorkspaceInfo;
import vn.tsolve.workspace.repository.WorkspaceInfoRepository;
import vn.tsolve.workspace.service.WorkspaceService;
import vn.tsolve.workspace.site.JiraSiteLinkRefusedException;
import vn.tsolve.workspace.site.JiraSiteLinker;
import vn.tsolve.workspace.site.JiraSiteRule;

@Service
public class WorkspaceServiceImpl implements WorkspaceService {
    private static final Logger log = LoggerFactory.getLogger(WorkspaceServiceImpl.class);
    private static final Pattern EMAIL = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
    private final TenantScope scope;
    private final WorkspaceInfoRepository repository;
    private final EntityManager entities;
    private final TicketTrackerClient tracker;
    private final JiraSiteLinker linker;
    private final TokenCipher cipher;
    private final AuditService audit;
    private final WorkspaceInfoMapper mapper;

    public WorkspaceServiceImpl(
            TenantScope scope,
            WorkspaceInfoRepository repository,
            EntityManager entities,
            TicketTrackerClient tracker,
            JiraSiteLinker linker,
            TokenCipher cipher,
            AuditService audit,
            WorkspaceInfoMapper mapper) {
        this.scope = scope;
        this.repository = repository;
        this.entities = entities;
        this.tracker = tracker;
        this.linker = linker;
        this.cipher = cipher;
        this.audit = audit;
        this.mapper = mapper;
    }

    @Override
    @Transactional
    public WorkspaceInfoResponse setup(JsonNode input) {
        if (input == null || !input.isObject()) {
            throw error(null, "MSG03", Map.of());
        }
        String companyName = field(input, "companyName", "Company name", 100).strip();
        String rawSite = field(input, "jiraSiteUrl", "Jira site address", 200);
        String email = field(input, "serviceAccountEmail", "Service account email", 100)
                .strip();
        String token = field(input, "serviceAccountToken", "Service account API token", null);
        if (!EMAIL.matcher(email).matches()) {
            throw error("serviceAccountEmail", "MSG03", Map.of());
        }
        String site = JiraSiteRule.normalise(rawSite).orElseThrow(() -> error("jiraSiteUrl", "MSG61", Map.of()));
        UUID workspaceId = scope.currentWorkspaceId();
        if (repository.count() != 0) {
            throw alreadySetUp();
        }
        try {
            tracker.checkServiceAccount(new JiraAccount(site, email, token));
        } catch (JiraRejectedException | JiraUnavailableException failure) {
            // The underlying Jira exception/body must never be propagated with the credential error.
            throw error("serviceAccountToken", "MSG156", Map.of("tracker_name", "Jira"));
        }
        try {
            linker.linkJiraSite(site);
        } catch (JiraSiteLinkRefusedException failure) {
            if (failure.getReason() == JiraSiteLinkRefusedException.Reason.LINKED_ELSEWHERE) {
                throw error("jiraSiteUrl", "MSG153", Map.of("tracker_name", "Jira"));
            }
            log.warn("Jira tenant information could not be read during Workspace setup");
            throw error("jiraSiteUrl", "MSG61", Map.of());
        }
        WorkspaceInfo workspace =
                new WorkspaceInfo(workspaceId, companyName, site, email, cipher.encrypt(workspaceId, token));
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
                Map.of("companyName", companyName, "jiraSiteUrl", site, "serviceAccountEmail", email));
        return mapper.toResponse(workspace);
    }

    @Override
    @Transactional(readOnly = true)
    public WorkspaceInfoResponse get() {
        return mapper.toResponse(repository
                .findById(scope.currentWorkspaceId())
                .orElseThrow(() -> new NotFoundException("MSG60", Map.of())));
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
