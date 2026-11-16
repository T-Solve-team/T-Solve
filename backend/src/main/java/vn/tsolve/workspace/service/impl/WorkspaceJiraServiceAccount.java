package vn.tsolve.workspace.service.impl;

import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.tsolve.identity.signin.port.TenantScope;
import vn.tsolve.tracker.dto.JiraAccount;
import vn.tsolve.tracker.service.JiraServiceAccount;
import vn.tsolve.worker.service.WorkerTenantService;
import vn.tsolve.workspace.dto.JiraAppToken;
import vn.tsolve.workspace.entity.WorkspaceInfo;
import vn.tsolve.workspace.repository.WorkspaceInfoRepository;
import vn.tsolve.workspace.service.JiraAppTokenService;

/** Jira access as the T-Solve Jira App: the Workspace's site and the app token held by STORY-012 (KAN-349). */
@Service
public class WorkspaceJiraServiceAccount implements JiraServiceAccount {

    private static final Logger log = LoggerFactory.getLogger(WorkspaceJiraServiceAccount.class);

    private final TenantScope scope;
    private final WorkspaceInfoRepository repository;
    private final JiraAppTokenService tokens;
    private final ObjectProvider<WorkerTenantService> workers;

    public WorkspaceJiraServiceAccount(
            TenantScope scope,
            WorkspaceInfoRepository repository,
            JiraAppTokenService tokens,
            ObjectProvider<WorkerTenantService> workers) {
        this.scope = scope;
        this.repository = repository;
        this.tokens = tokens;
        this.workers = workers;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<JiraAccount> current() {
        // D28: a worker job binds only WorkerTenantService (bean present with the worker config); a web request only
        // TenantScope.
        WorkerTenantService worker = workers.getIfAvailable();
        UUID workspaceId = Optional.ofNullable(worker)
                .flatMap(WorkerTenantService::currentWorkspaceId)
                .orElseGet(scope::currentWorkspaceId);
        Optional<WorkspaceInfo> found = repository.findById(workspaceId);
        if (found.isEmpty()) {
            return Optional.empty();
        }
        WorkspaceInfo workspace = found.get();
        String siteUrl = workspace.getJiraSiteAddress();
        String cloudId = workspace.getJiraCloudId();
        Optional<JiraAppToken> token = tokens.current(workspaceId);
        if (isBlank(siteUrl) || token.isEmpty()) {
            return Optional.empty();
        }
        // AC-008.4: a token of another site is never used for this Workspace
        if (!token.get().cloudId().equals(cloudId)) {
            log.warn("Workspace {} holds a Jira App token of another site; it is not used", workspaceId);
            return Optional.empty();
        }
        return Optional.of(new JiraAccount(siteUrl, cloudId, token.get().token()));
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
