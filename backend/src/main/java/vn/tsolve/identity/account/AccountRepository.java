package vn.tsolve.identity.account;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AccountRepository extends JpaRepository<Account, UUID> {

    /** The sign-in lookup: an exact match on the lower-cased unique column, so the unique index is used. */
    Optional<Account> findByEmail(String email);

    /** The sign-in lookup by the bound Keycloak subject (unique). */
    Optional<Account> findByKeycloakSub(String sub);

    /**
     * Binds the sub to an Active Account that holds none or already holds this same sub; returns the rows changed (0 =
     * deactivated meanwhile, or another sub was bound).
     */
    @Modifying
    @Query("update Account a set a.keycloakSub = :sub where a.id = :id and a.status = vn.tsolve.identity.account"
            + ".AccountStatus.ACTIVE and (a.keycloakSub is null or a.keycloakSub = :sub)")
    int bindKeycloakSub(@Param("id") UUID id, @Param("sub") String sub);

    /**
     * Records a sign-in on an Active Account only: the name when given, the last time, and the first time once. Never
     * touches the status or the sub, so a concurrent deactivation is not undone.
     */
    @Modifying
    @Query("update Account a set a.fullName = coalesce(:name, a.fullName), a.lastSignInAt = :now,"
            + " a.firstSignInAt = coalesce(a.firstSignInAt, :now) where a.id = :id and a.status ="
            + " vn.tsolve.identity.account.AccountStatus.ACTIVE")
    int recordSignIn(@Param("id") UUID id, @Param("name") String name, @Param("now") java.time.Instant now);
}
