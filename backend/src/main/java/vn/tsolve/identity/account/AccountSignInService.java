package vn.tsolve.identity.account;

import java.time.Instant;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionOperations;
import vn.tsolve.identity.signin.IdentityClock;

/**
 * Looks up the Account of a sign-in and records it. The caller binds the Workspace with {@code TenantScope.runIn}, so
 * the Account is read and written in that Workspace's database only.
 */
@Service
public class AccountSignInService {

    public enum Status {
        SIGNED_IN,
        NO_ACCOUNT,
        DEACTIVATED,
        SUB_CONFLICT
    }

    public record Result(Status status, UUID accountId) {}

    /** The length of the {@code keycloak_sub} column. */
    private static final int SUB_MAX = 64;

    private static final Logger LOG = LoggerFactory.getLogger(AccountSignInService.class);

    private final AccountRepository accounts;
    private final IdentityClock clock;
    private final TransactionOperations transactions;

    public AccountSignInService(AccountRepository accounts, IdentityClock clock, TransactionOperations transactions) {
        this.accounts = accounts;
        this.clock = clock;
        this.transactions = transactions;
    }

    /**
     * Finds the Account by the token {@code sub}, or, when none holds it, by email (any case). A sub-less Active
     * Account gets the sub bound by a conditional update; a different sub, or a lost bind, is a SUB_CONFLICT. An
     * Active Account then gets its full name from the {@code name} claim and the sign-in time (AC-001.12, .13, .16,
     * .56).
     *
     * <p>Not {@code @Transactional}: a unique violation inside one transaction would leave it rollback-only, so the
     * bind and the save each run in their own repository transaction and the violation is handled here.
     */
    public Result signIn(String sub, String email, String name) {
        if (!usableSub(sub)) {
            return new Result(Status.NO_ACCOUNT, null);
        }
        Account account;
        Optional<Account> bySub = accounts.findByKeycloakSub(sub);
        if (bySub.isPresent()) {
            account = bySub.get();
            if (account.getStatus() != AccountStatus.ACTIVE) {
                return new Result(Status.DEACTIVATED, account.getId());
            }
        } else {
            // the stored email is lower-cased, so an exact match on it uses the unique index
            Optional<Account> byEmail = accounts.findByEmail(email.toLowerCase(Locale.ROOT));
            if (byEmail.isEmpty()) {
                return new Result(Status.NO_ACCOUNT, null);
            }
            account = byEmail.get();
            if (account.getKeycloakSub() != null && !account.getKeycloakSub().equals(sub)) {
                return conflict(account);
            }
            if (account.getStatus() != AccountStatus.ACTIVE) {
                return new Result(Status.DEACTIVATED, account.getId());
            }
            if (account.getKeycloakSub() == null) {
                int bound;
                try {
                    bound = bind(account.getId(), sub);
                } catch (DataIntegrityViolationException e) {
                    bound = 0;
                }
                if (bound == 0) {
                    // the bind refuses a deactivated row too: re-read to tell the two apart
                    Optional<Account> current = accounts.findById(account.getId());
                    if (current.isPresent() && current.get().getStatus() != AccountStatus.ACTIVE) {
                        return new Result(Status.DEACTIVATED, account.getId());
                    }
                    return conflict(account);
                }
                account.bindKeycloakSub(sub);
            }
        }
        String fullName = usableName(name);
        if (fullName != null) {
            account.rename(fullName);
        }
        Instant now = clock.now();
        account.recordSignIn(now);
        // a targeted update (active rows only): a save would write every column back and could undo a deactivation
        UUID id = account.getId();
        transactions.execute(status -> accounts.recordSignIn(id, fullName, now));
        return new Result(Status.SIGNED_IN, account.getId());
    }

    /** The conditional update needs a transaction of its own (the repository method carries none). */
    private int bind(UUID id, String sub) {
        Integer rows = transactions.execute(status -> accounts.bindKeycloakSub(id, sub));
        return rows == null ? 0 : rows;
    }

    /** A sub that is present, not blank (Unicode spaces count) and fits the column. */
    private static boolean usableSub(String sub) {
        if (sub == null || sub.length() > SUB_MAX) {
            return false;
        }
        for (int i = 0; i < sub.length(); i++) {
            if (!isSpace(sub.charAt(i))) {
                return true;
            }
        }
        return false;
    }

    /** A match failure (MSG09): one WARN with the Account id only, nothing saved. */
    private static Result conflict(Account account) {
        LOG.warn("Sign-in refused: the Keycloak subject does not match Account {}", account.getId());
        return new Result(Status.SUB_CONFLICT, account.getId());
    }

    /** The trimmed name, or null when the claim is absent, blank or longer than the column (the old name stays). */
    private static String usableName(String name) {
        if (name == null) {
            return null;
        }
        String trimmed = trimSpaces(name);
        if (trimmed.isEmpty() || trimmed.codePointCount(0, trimmed.length()) > Account.FULL_NAME_MAX) {
            return null;
        }
        return trimmed;
    }

    /** Trims whitespace and Unicode space separators (NBSP, U+3000), which {@code String.trim()} keeps. */
    private static String trimSpaces(String s) {
        int start = 0;
        int end = s.length();
        while (start < end && isSpace(s.charAt(start))) {
            start++;
        }
        while (end > start && isSpace(s.charAt(end - 1))) {
            end--;
        }
        return s.substring(start, end);
    }

    private static boolean isSpace(char c) {
        return Character.isWhitespace(c) || Character.isSpaceChar(c);
    }
}
