package vn.tsolve.identity.account;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

/** A person's Account in one Workspace's database. */
@Entity
@Table(name = "app_user")
public class Account {

    /** The length of the {@code full_name} column. */
    public static final int FULL_NAME_MAX = 100;

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id")
    private UUID id;

    @Column(nullable = false)
    private String email;

    @Column(name = "full_name", length = FULL_NAME_MAX)
    private String fullName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AccountStatus status;

    @Column(name = "first_sign_in_at")
    private Instant firstSignInAt;

    @Column(name = "last_sign_in_at")
    private Instant lastSignInAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Account() {}

    public Account(String email, String fullName, AccountStatus status, Instant createdAt) {
        // Locale.ROOT: the default locale (Turkish dotless i) must not change the stored email
        this.email = email.toLowerCase(Locale.ROOT);
        this.fullName = fullName;
        this.status = status;
        this.createdAt = createdAt;
    }

    /** Sets the name shown for the Account (AC-001.16). */
    public void rename(String newFullName) {
        this.fullName = newFullName;
    }

    /** Records a sign-in at {@code now}; the first sign-in time is set once and never moves. */
    public void recordSignIn(Instant now) {
        if (firstSignInAt == null) {
            firstSignInAt = now;
        }
        lastSignInAt = now;
    }

    public UUID getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public String getFullName() {
        return fullName;
    }

    public AccountStatus getStatus() {
        return status;
    }

    public Instant getFirstSignInAt() {
        return firstSignInAt;
    }

    public Instant getLastSignInAt() {
        return lastSignInAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
