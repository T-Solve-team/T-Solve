package vn.tsolve.identity.account;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AccountRepository extends JpaRepository<Account, UUID> {

    /** The sign-in lookup: an exact match on the lower-cased unique column, so the unique index is used. */
    Optional<Account> findByEmail(String email);
}
