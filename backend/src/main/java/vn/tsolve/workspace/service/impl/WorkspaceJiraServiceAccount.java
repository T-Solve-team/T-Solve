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
import vn.tsolve.workspace.crypto.TokenCipher;
import vn.tsolve.workspace.entity.WorkspaceInfo;
import vn.tsolve.workspace.repository.WorkspaceInfoRepository;

@Service
public class WorkspaceJiraServiceAccount implements JiraServiceAccount {

    private static final Logger log = LoggerFactory.getLogger(WorkspaceJiraServiceAccount.class);

    private final TenantScope scope;
    private final WorkspaceInfoRepository repository;
    private final TokenCipher cipher;
    private final ObjectProvider<WorkerTenantService> workers;

    public WorkspaceJiraServiceAccount(
            TenantScope scope,
            WorkspaceInfoRepository repository,
            TokenCipher cipher,
            ObjectProvider<WorkerTenantService> workers) {
        this.scope = scope;
        this.repository = repository;
        this.cipher = cipher;
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
        String encryptedToken = workspace.getServiceAccountApiToken();
        // AC-008.4: a row set up before KAN-344 has no cloud id until the Admin enters a new token.
        if (isBlank(siteUrl) || isBlank(cloudId) || isBlank(encryptedToken)) {
            return Optional.empty();
        }
        try {
            String token = cipher.decrypt(workspaceId, encryptedToken);
            return Optional.of(new JiraAccount(siteUrl, cloudId, token));
        } catch (IllegalStateException exception) {
            log.error("Workspace {} Jira service account token could not be decrypted", workspaceId);
            return Optional.empty();
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
