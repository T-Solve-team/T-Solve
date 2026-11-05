package vn.tsolve.identity.account;

import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.tsolve.identity.signin.IdentityClock;

/**
 * Looks up the Account of a sign-in and records it, in one transaction. The caller opens the transaction inside
 * {@code TenantScope.runIn}, so the Account is read and written in the state's Workspace database only.
 */
@Service
public class AccountSignInService {

    public enum Status {
        SIGNED_IN,
        NO_ACCOUNT,
        DEACTIVATED
    }

    public record Result(Status status, UUID accountId) {}

    private final AccountRepository accounts;
    private final IdentityClock clock;

    public AccountSignInService(AccountRepository accounts, IdentityClock clock) {
        this.accounts = accounts;
        this.clock = clock;
    }

    /**
     * Finds the Account by email (any case); when it is Active, sets its full name from the {@code name} claim and
     * records the sign-in time (AC-001.12, .13, .16).
     */
    @Transactional
    public Result signIn(String email, String name) {
        // the stored email is lower-cased, so an exact match on it uses the unique index
        Optional<Account> found = accounts.findByEmail(email.toLowerCase(Locale.ROOT));
        if (found.isEmpty()) {
            return new Result(Status.NO_ACCOUNT, null);
        }
        Account account = found.get();
        if (account.getStatus() != AccountStatus.ACTIVE) {
            return new Result(Status.DEACTIVATED, account.getId());
        }
        String fullName = usableName(name);
        if (fullName != null) {
            account.rename(fullName);
        }
        account.recordSignIn(clock.now());
        accounts.save(account);
        return new Result(Status.SIGNED_IN, account.getId());
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
