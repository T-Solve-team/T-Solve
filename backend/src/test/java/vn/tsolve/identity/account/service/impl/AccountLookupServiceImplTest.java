package vn.tsolve.identity.account.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import vn.tsolve.common.security.Role;
import vn.tsolve.common.security.TSolveUser;
import vn.tsolve.identity.account.Account;
import vn.tsolve.identity.account.AccountRepository;
import vn.tsolve.identity.account.AccountStatus;

class AccountLookupServiceImplTest {

    private static final UUID ID = UUID.fromString("10000000-0000-0000-0000-000000000001");

    private final AccountRepository repository = mock(AccountRepository.class);
    private final AccountLookupServiceImpl service = new AccountLookupServiceImpl(repository);

    @Test
    void findsAnActiveAccountAsAnEmployee() throws Exception {
        when(repository.findById(ID))
                .thenReturn(Optional.of(account("An@T-Solve.vn", "Nguyễn An", AccountStatus.ACTIVE)));

        Optional<TSolveUser> user = service.findActive(ID);

        assertThat(user).hasValueSatisfying(found -> {
            assertThat(found.id()).isEqualTo(ID);
            assertThat(found.email()).isEqualTo("an@t-solve.vn");
            assertThat(found.fullName()).isEqualTo("Nguyễn An");
            assertThat(found.roles()).containsExactly(Role.EMPLOYEE);
        });
    }

    @Test
    void hidesADeactivatedAccount() throws Exception {
        when(repository.findById(ID))
                .thenReturn(Optional.of(account("an@t-solve.vn", "An", AccountStatus.DEACTIVATED)));

        assertThat(service.findActive(ID)).isEmpty();
    }

    @Test
    void findsNothingForAnUnknownId() {
        when(repository.findById(ID)).thenReturn(Optional.empty());

        assertThat(service.findActive(ID)).isEmpty();
    }

    @Test
    void givesAnAccountWithoutANameAnEmptyName() throws Exception {
        when(repository.findById(ID)).thenReturn(Optional.of(account("an@t-solve.vn", null, AccountStatus.ACTIVE)));

        assertThat(service.findActive(ID))
                .hasValueSatisfying(found -> assertThat(found.fullName()).isEmpty());
    }

    private static Account account(String email, String fullName, AccountStatus status) throws Exception {
        Account account = new Account(email, fullName, status, Instant.parse("2026-11-01T00:00:00Z"));
        Field id = Account.class.getDeclaredField("id");
        id.setAccessible(true);
        id.set(account, ID);
        return account;
    }
}
