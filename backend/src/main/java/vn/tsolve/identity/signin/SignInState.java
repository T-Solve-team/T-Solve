package vn.tsolve.identity.signin;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * One sign-in attempt, kept on the server. The browser only gets {@code <id>.<mac>}; the nonce and the PKCE
 * verifier never leave the server (AC-001.2). The table is read before any Workspace is known, so it is not a
 * tenant table.
 */
@Entity
@Table(name = "sign_in_states")
public class SignInState {

    public enum Target {
        WORKSPACE,
        PLATFORM_CONSOLE,
        JIRA_LINK
    }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private Target target;

    @Column(name = "workspace_id", updatable = false)
    private UUID workspaceId;

    @Column(name = "tracker_account_id", updatable = false)
    private String trackerAccountId;

    @Column(name = "return_to", nullable = false, updatable = false)
    private String returnTo;

    @Column(nullable = false, updatable = false)
    private String nonce;

    @Column(name = "pkce_verifier", nullable = false, updatable = false)
    private String pkceVerifier;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false, updatable = false)
    private Instant expiresAt;

    // only SignInStateRepository.markUsed writes it, in one conditional UPDATE
    @Column(name = "used_at")
    private Instant usedAt;

    protected SignInState() {}

    SignInState(
            Target target,
            UUID workspaceId,
            String trackerAccountId,
            String returnTo,
            String nonce,
            String pkceVerifier,
            Instant createdAt,
            Instant expiresAt) {
        this.target = target;
        this.workspaceId = workspaceId;
        this.trackerAccountId = trackerAccountId;
        this.returnTo = returnTo;
        this.nonce = nonce;
        this.pkceVerifier = pkceVerifier;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
    }

    public UUID getId() {
        return id;
    }

    public Target getTarget() {
        return target;
    }

    public UUID getWorkspaceId() {
        return workspaceId;
    }

    public String getTrackerAccountId() {
        return trackerAccountId;
    }

    public String getReturnTo() {
        return returnTo;
    }

    public String getNonce() {
        return nonce;
    }

    public String getPkceVerifier() {
        return pkceVerifier;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getUsedAt() {
        return usedAt;
    }
}
