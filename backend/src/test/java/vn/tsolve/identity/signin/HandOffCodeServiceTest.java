package vn.tsolve.identity.signin;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class HandOffCodeServiceTest {

    private static final UUID WORKSPACE = UUID.fromString("00000000-0000-4000-8000-00000000000a");
    private static final UUID ACCOUNT = UUID.fromString("00000000-0000-4000-8000-0000000000a1");

    private final HandOffStore store = new HandOffStore();
    private final SignedToken signedToken = new SignedToken(SignInTestFixtures.properties());
    private HandOffCodeService service;

    @BeforeEach
    void setUp() {
        IdentityClock clock = new IdentityClock(Clock.fixed(MutableClock.START, java.time.ZoneOffset.UTC));
        service = new HandOffCodeService(store.repository(), signedToken, clock);
    }

    @Test
    @DisplayName("AC-001.16 issue stores a Workspace code for the Account, site and returnTo, valid for 60 seconds")
    void issueStoresAWorkspaceCode_AC_001_16() {
        service.issue(WORKSPACE, ACCOUNT, "acme.localhost", "/tickets/42");

        HandOffCode row = store.onlySaved();
        assertThat(row.getTarget()).isEqualTo(HandOffCode.Target.WORKSPACE);
        assertThat(row.getWorkspaceId()).isEqualTo(WORKSPACE);
        assertThat(row.getAccountId()).isEqualTo(ACCOUNT);
        assertThat(row.getOperatorEmail()).isNull();
        assertThat(row.getSiteHost()).isEqualTo("acme.localhost");
        assertThat(row.getReturnTo()).isEqualTo("/tickets/42");
        assertThat(row.getCreatedAt()).isEqualTo(MutableClock.START);
        assertThat(row.getExpiresAt()).isEqualTo(MutableClock.START.plus(Duration.ofSeconds(60)));
        assertThat(row.getUsedAt()).isNull();
    }

    @Test
    @DisplayName("AC-001.16 issue returns a signed <id>.<mac> value whose payload is the stored id")
    void issueReturnsASignedValue_AC_001_16() {
        String code = service.issue(WORKSPACE, ACCOUNT, "acme.localhost", "/");

        HandOffCode row = store.onlySaved();
        assertThat(signedToken.verify(code)).contains(row.getId().toString());
        SignedToken other = new SignedToken(SignInTestFixtures.properties(SignInTestFixtures.OTHER_KEY));
        assertThat(other.verify(code)).isEmpty();
    }

    @Test
    @DisplayName("AC-001.16 issue makes a different code each time")
    void issueMakesADifferentCodeEachTime_AC_001_16() {
        String first = service.issue(WORKSPACE, ACCOUNT, "acme.localhost", "/");
        String second = service.issue(WORKSPACE, ACCOUNT, "acme.localhost", "/");

        assertThat(store.saved()).hasSize(2);
        assertThat(store.saved().get(0).getId())
                .isNotEqualTo(store.saved().get(1).getId());
        assertThat(first).isNotEqualTo(second);
    }
}
