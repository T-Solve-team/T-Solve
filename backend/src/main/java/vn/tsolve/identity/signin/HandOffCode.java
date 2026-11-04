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

/** A single-use code that hands a signed-in Account from {@code login.} to the target site. */
@Entity
@Table(name = "hand_off_code")
public class HandOffCode {

    public enum Target {
        WORKSPACE,
        PLATFORM_CONSOLE
    }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id")
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private Target target;

    @Column(name = "workspace_id", updatable = false)
    private UUID workspaceId;

    @Column(name = "account_id", updatable = false)
    private UUID accountId;

    @Column(name = "site_host", nullable = false, updatable = false)
    private String siteHost;

    @Column(name = "return_to", nullable = false, updatable = false)
    private String returnTo;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false, updatable = false)
    private Instant expiresAt;

    @Column(name = "used_at")
    private Instant usedAt;

    @Column(name = "operator_email", updatable = false)
    private String operatorEmail;

    protected HandOffCode() {}

    /** A code for a Workspace sign-in; {@code operator_email} stays null. */
    static HandOffCode forWorkspace(
            UUID workspaceId, UUID accountId, String siteHost, String returnTo, Instant createdAt, Instant expiresAt) {
        HandOffCode code = new HandOffCode();
        code.target = Target.WORKSPACE;
        code.workspaceId = workspaceId;
        code.accountId = accountId;
        code.siteHost = siteHost;
        code.returnTo = returnTo;
        code.createdAt = createdAt;
        code.expiresAt = expiresAt;
        return code;
    }

    public String getOperatorEmail() {
        return operatorEmail;
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

    public UUID getAccountId() {
        return accountId;
    }

    public String getSiteHost() {
        return siteHost;
    }

    public String getReturnTo() {
        return returnTo;
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
