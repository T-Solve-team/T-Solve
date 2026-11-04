package vn.tsolve.identity.signin;

import java.time.Instant;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface SignInStateRepository extends JpaRepository<SignInState, UUID> {

    /**
     * Marks a state used, in one conditional UPDATE, so two requests with the same state cannot both win (single
     * use, AC-001.2). Returns 1 for the winner and 0 when the state is unknown, already used or expired at
     * {@code usedAt}.
     */
    @Transactional
    @Modifying
    @Query("update SignInState s set s.usedAt = :usedAt"
            + " where s.id = :id and s.usedAt is null and s.expiresAt > :usedAt")
    int markUsed(@Param("id") UUID id, @Param("usedAt") Instant usedAt);

    /**
     * Binds the state to a browser, once: 1 for the winner, 0 when the state is unknown, used, already authorized or
     * expired at {@code now} (AC-001.44/.45). {@code now} comes from the caller, not from the database.
     */
    @Transactional
    @Modifying
    @Query("update SignInState s set s.authorizedAt = :now, s.browserBindingHash = :hash"
            + " where s.id = :id and s.usedAt is null and s.authorizedAt is null and s.expiresAt > :now")
    int markAuthorized(@Param("id") UUID id, @Param("hash") byte[] bindingHash, @Param("now") Instant now);
}
