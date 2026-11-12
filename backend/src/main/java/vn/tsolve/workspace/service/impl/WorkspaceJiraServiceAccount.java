package vn.tsolve.workspace.service.impl;

import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.tsolve.identity.signin.port.TenantScope;
import vn.tsolve.tracker.dto.JiraAccount;
import vn.tsolve.tracker.service.JiraServiceAccount;
import vn.tsolve.workspace.crypto.TokenCipher;
import vn.tsolve.workspace.entity.WorkspaceInfo;
import vn.tsolve.workspace.repository.WorkspaceInfoRepository;

@Service
public class WorkspaceJiraServiceAccount implements JiraServiceAccount {

    private static final Logger log = LoggerFactory.getLogger(WorkspaceJiraServiceAccount.class);

    private final TenantScope scope;
    private final WorkspaceInfoRepository repository;
    private final TokenCipher cipher;

    public WorkspaceJiraServiceAccount(TenantScope scope, WorkspaceInfoRepository repository, TokenCipher cipher) {
        this.scope = scope;
        this.repository = repository;
        this.cipher = cipher;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<JiraAccount> current() {
        UUID workspaceId = scope.currentWorkspaceId();
        Optional<WorkspaceInfo> found = repository.findById(workspaceId);
        if (found.isEmpty()) {
            return Optional.empty();
        }
        WorkspaceInfo workspace = found.get();
        String siteUrl = workspace.getJiraSiteAddress();
        String email = workspace.getServiceAccountEmail();
        String encryptedToken = workspace.getServiceAccountApiToken();
        if (isBlank(siteUrl) || isBlank(email) || isBlank(encryptedToken)) {
            return Optional.empty();
        }
        try {
            String token = cipher.decrypt(workspaceId, encryptedToken);
            return Optional.of(new JiraAccount(siteUrl, email, token));
        } catch (IllegalStateException exception) {
            log.error("Workspace {} Jira service account token could not be decrypted", workspaceId);
            return Optional.empty();
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
