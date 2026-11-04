package vn.tsolve.identity.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import vn.tsolve.identity.signin.IdentityClock;

class AccountSignInServiceNameTest {

    private static final Instant NOW = Instant.parse("2026-11-04T08:00:00Z");

    /** The Database Tables PDF column is full_name VARCHAR(100); the limit counts code points, not UTF-16 units. */
    private static final int COLUMN = 100;

    private final AccountRepository accounts = mock(AccountRepository.class);
    private final AccountSignInService service =
            new AccountSignInService(accounts, new IdentityClock(Clock.fixed(NOW, java.time.ZoneOffset.UTC)));

    private Account signInWithName(String name) {
        Account lan = new Account("lan@acme.example", "Old Name", AccountStatus.ACTIVE, NOW.minusSeconds(60));
        when(accounts.findByEmail(any())).thenReturn(Optional.of(lan));
        AccountSignInService.Result result = service.signIn("lan@acme.example", name);
        assertThat(result.status()).isEqualTo(AccountSignInService.Status.SIGNED_IN);
        return lan;
    }

    private static String grinning(int count) {
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < count; i++) {
            text.appendCodePoint(0x1F600);
        }
        return text.toString();
    }

    @Test
    @DisplayName("AC-001.16 keeps the old full name when the name claim is longer than 100, and still signs in")
    void keepsTheOldNameWhenTheClaimIsLongerThanTheColumn_AC_001_16() {
        Account lan = signInWithName("x".repeat(COLUMN + 1));

        assertThat(lan.getFullName()).isEqualTo("Old Name");
        assertThat(lan.getLastSignInAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("AC-001.16 stores a name that is exactly 100 characters long")
    void storesANameExactlyAsLongAsTheColumn_AC_001_16() {
        String longest = "y".repeat(COLUMN);

        Account lan = signInWithName(longest);

        assertThat(lan.getFullName()).isEqualTo(longest);
    }

    @Test
    @DisplayName("AC-001.16 counts code points: 100 characters outside the basic plane (200 UTF-16 units) are stored")
    void storesHundredNonBmpCodePoints_AC_001_16() {
        String hundred = grinning(COLUMN);
        assertThat(hundred.length()).isEqualTo(2 * COLUMN);

        Account lan = signInWithName(hundred);

        assertThat(lan.getFullName()).isEqualTo(hundred);
    }

    @Test
    @DisplayName("AC-001.16 keeps the old name when the claim has 101 code points outside the basic plane")
    void keepsTheOldNameForHundredAndOneNonBmpCodePoints_AC_001_16() {
        Account lan = signInWithName(grinning(COLUMN + 1));

        assertThat(lan.getFullName()).isEqualTo("Old Name");
        assertThat(lan.getLastSignInAt()).isEqualTo(NOW);
    }

    @ParameterizedTest(name = "[{index}] name made of code points {0}")
    @ValueSource(strings = {"00A0", "3000", "3000 00A0 0020"})
    @DisplayName("AC-001.16 / Q2 a name made only of NBSP, U+3000 or spaces leaves the old name unchanged")
    void aNameOfOnlyUnicodeSpacesLeavesTheNameUnchanged_AC_001_16(String codePoints) {
        StringBuilder name = new StringBuilder();
        for (String hex : codePoints.split(" ")) {
            name.appendCodePoint(Integer.parseInt(hex, 16));
        }

        Account lan = signInWithName(name.toString());

        assertThat(lan.getFullName()).isEqualTo("Old Name");
        assertThat(lan.getLastSignInAt()).isEqualTo(NOW);
    }
}
