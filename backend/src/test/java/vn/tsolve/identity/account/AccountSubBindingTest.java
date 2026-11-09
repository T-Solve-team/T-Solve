package vn.tsolve.identity.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;
import vn.tsolve.identity.signin.IdentityClock;

/** AC-001.12/.13/.16/.56: the Account of a sign-in is matched by the token's sub, or bound on its first sign-in. */
class AccountSubBindingTest {

    private static final Instant T0 = Instant.parse("2026-11-04T08:00:00Z");
    private static final Instant T1 = Instant.parse("2026-11-05T09:30:00Z");
    private static final String SUB_1 = "kc-sub-1";
    private static final String SUB_2 = "kc-sub-2";
    private static final String LAN = "lan@acme.example";

    /** A clock the test moves from T0 to T1. */
    private static final class MutableClock extends Clock {
        private Instant now = T0;

        void set(Instant instant) {
            now = instant;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    private final AccountRepository accounts = mock(AccountRepository.class);
    private final MutableClock clock = new MutableClock();
    private final AccountSignInService service = new AccountSignInService(
            accounts,
            new IdentityClock(clock),
            org.springframework.transaction.support.TransactionOperations.withoutTransaction());

    private final Logger serviceLogger = (Logger) LoggerFactory.getLogger(AccountSignInService.class);
    private ListAppender<ILoggingEvent> logs;

    @BeforeEach
    void captureLogs() {
        logs = new ListAppender<>();
        logs.start();
        serviceLogger.addAppender(logs);
        when(accounts.findByKeycloakSub(anyString())).thenReturn(Optional.empty());
        when(accounts.findByEmail(anyString())).thenReturn(Optional.empty());
        when(accounts.bindKeycloakSub(any(UUID.class), anyString())).thenReturn(1);
    }

    @AfterEach
    void releaseLogs() {
        serviceLogger.detachAppender(logs);
    }

    private static Account account(String email, AccountStatus status, String sub) {
        Account account = new Account(email, "Old Name", status, T0.minusSeconds(3600));
        ReflectionTestUtils.setField(account, "id", UUID.randomUUID());
        if (sub != null) {
            account.bindKeycloakSub(sub);
        }
        return account;
    }

    private List<ILoggingEvent> warnings() {
        return logs.list.stream().filter(e -> e.getLevel() == Level.WARN).toList();
    }

    @Test
    @DisplayName(
            "AC-001.12 a first sign-in binds the token sub to the sub-less Account whose email matches ignoring case")
    void aFirstSignInBindsTheSub_AC_001_12() {
        Account lan = account(LAN, AccountStatus.ACTIVE, null);
        when(accounts.findByEmail(LAN)).thenReturn(Optional.of(lan));

        AccountSignInService.Result result = service.signIn(SUB_1, "LAN@Acme.Example", "Lan Tran");

        assertThat(result.status()).isEqualTo(AccountSignInService.Status.SIGNED_IN);
        assertThat(result.accountId()).isEqualTo(lan.getId());
        verify(accounts).findByEmail(LAN);
        verify(accounts).bindKeycloakSub(lan.getId(), SUB_1);
    }

    @Test
    @DisplayName("AC-001.12 no Account with the sub and none with the email answers NO_ACCOUNT and changes nothing")
    void noAccountAnswersNoAccountAndChangesNothing_AC_001_12() {
        AccountSignInService.Result result = service.signIn(SUB_1, LAN, "Lan Tran");

        assertThat(result.status()).isEqualTo(AccountSignInService.Status.NO_ACCOUNT);
        assertThat(result.accountId()).isNull();
        verify(accounts, never()).save(any());
        verify(accounts, never()).bindKeycloakSub(any(UUID.class), anyString());
        assertThat(warnings()).isEmpty();
    }

    @Test
    @DisplayName("edge 119 a later sign-in matches by sub when the token email differs and keeps app_user.email")
    void aLaterSignInMatchesBySubAndKeepsTheEmail_edge119() {
        Account lan = account(LAN, AccountStatus.ACTIVE, SUB_1);
        when(accounts.findByKeycloakSub(SUB_1)).thenReturn(Optional.of(lan));

        AccountSignInService.Result result = service.signIn(SUB_1, "lan.new@acme.example", "Lan Tran");

        assertThat(result.status()).isEqualTo(AccountSignInService.Status.SIGNED_IN);
        assertThat(result.accountId()).isEqualTo(lan.getId());
        assertThat(lan.getEmail()).isEqualTo(LAN);
        assertThat(lan.getKeycloakSub()).isEqualTo(SUB_1);
        assertThat(lan.getLastSignInAt()).isEqualTo(T0);
    }

    @Test
    @DisplayName(
            "AC-001.56 / edge 113 an Account matched by email that holds a different sub is refused, unchanged, with one WARN naming only its id")
    void anAccountHoldingAnotherSubIsRefused_AC_001_56_edge113() {
        Account lan = account(LAN, AccountStatus.ACTIVE, SUB_1);
        when(accounts.findByEmail(LAN)).thenReturn(Optional.of(lan));

        AccountSignInService.Result result = service.signIn(SUB_2, LAN, "Someone Else");

        assertThat(result.status()).isEqualTo(AccountSignInService.Status.SUB_CONFLICT);
        assertThat(result.accountId()).isEqualTo(lan.getId());
        assertThat(lan.getKeycloakSub()).isEqualTo(SUB_1);
        assertThat(lan.getFullName()).isEqualTo("Old Name");
        assertThat(lan.getFirstSignInAt()).isNull();
        assertThat(lan.getLastSignInAt()).isNull();
        verify(accounts, never()).save(any());
        verify(accounts, never()).bindKeycloakSub(any(UUID.class), anyString());
        assertThat(warnings()).hasSize(1);
        String message = warnings().get(0).getFormattedMessage();
        assertThat(message).contains(lan.getId().toString()).doesNotContain(LAN, SUB_1, SUB_2);
    }

    @Test
    @DisplayName(
            "AC-001.56 a sub bound to Account X signs in as X even when the email matches a sub-less Account Y; Y is untouched")
    void theBoundSubWinsOverAnEmailMatch_AC_001_56() {
        Account x = account("old@acme.example", AccountStatus.ACTIVE, SUB_1);
        Account y = account("new@acme.example", AccountStatus.ACTIVE, null);
        when(accounts.findByKeycloakSub(SUB_1)).thenReturn(Optional.of(x));
        when(accounts.findByEmail("new@acme.example")).thenReturn(Optional.of(y));

        AccountSignInService.Result result = service.signIn(SUB_1, "new@acme.example", "Lan Tran");

        assertThat(result.status()).isEqualTo(AccountSignInService.Status.SIGNED_IN);
        assertThat(result.accountId()).isEqualTo(x.getId());
        assertThat(y.getKeycloakSub()).isNull();
        assertThat(y.getFirstSignInAt()).isNull();
        assertThat(y.getLastSignInAt()).isNull();
        assertThat(y.getFullName()).isEqualTo("Old Name");
        verify(accounts, never()).bindKeycloakSub(eqId(y), anyString());
        assertThat(warnings()).isEmpty();
    }

    private static UUID eqId(Account account) {
        return org.mockito.ArgumentMatchers.eq(account.getId());
    }

    @ParameterizedTest(name = "[{index}] matched by {0}")
    @ValueSource(strings = {"sub", "email on the first sign-in"})
    @DisplayName(
            "AC-001.13 a Deactivated Account is refused and not changed: matched by sub, or on its first sign-in by email (sub not bound)")
    void aDeactivatedAccountIsRefusedAndNotChanged_AC_001_13(String matchedBy) {
        Account lan;
        AccountSignInService.Result result;
        if (matchedBy.equals("sub")) {
            lan = account(LAN, AccountStatus.DEACTIVATED, SUB_1);
            when(accounts.findByKeycloakSub(SUB_1)).thenReturn(Optional.of(lan));
            result = service.signIn(SUB_1, "lan.new@acme.example", "Lan Tran");
        } else {
            lan = account(LAN, AccountStatus.DEACTIVATED, null);
            when(accounts.findByEmail(LAN)).thenReturn(Optional.of(lan));
            result = service.signIn(SUB_1, LAN, "Lan Tran");
        }

        assertThat(result.status()).isEqualTo(AccountSignInService.Status.DEACTIVATED);
        assertThat(result.accountId()).isEqualTo(lan.getId());
        assertThat(lan.getFullName()).isEqualTo("Old Name");
        assertThat(lan.getFirstSignInAt()).isNull();
        assertThat(lan.getLastSignInAt()).isNull();
        verify(accounts, never()).save(any());
        verify(accounts, never()).bindKeycloakSub(any(UUID.class), anyString());
        if (matchedBy.equals("sub")) {
            assertThat(lan.getKeycloakSub()).isEqualTo(SUB_1);
        } else {
            assertThat(lan.getKeycloakSub()).isNull();
        }
    }

    @Test
    @DisplayName(
            "AC-001.56 a Deactivated Account whose email matches but holds a different sub is a match failure (MSG09), not MSG10")
    void aDeactivatedAccountWithAnotherSubIsAMatchFailure_AC_001_56() {
        Account lan = account(LAN, AccountStatus.DEACTIVATED, SUB_1);
        when(accounts.findByEmail(LAN)).thenReturn(Optional.of(lan));

        AccountSignInService.Result result = service.signIn(SUB_2, LAN, "Lan Tran");

        assertThat(result.status()).isEqualTo(AccountSignInService.Status.SUB_CONFLICT);
        assertThat(result.accountId()).isEqualTo(lan.getId());
        assertThat(warnings()).hasSize(1);
        verify(accounts, never()).save(any());
    }

    @Test
    @DisplayName(
            "AC-001.16 the first sign-in sets the full name and both times; a later sign-in by sub moves only the last time")
    void theFirstSignInSetsBothTimesAndALaterOneMovesOnlyTheLast_AC_001_16() {
        Account lan = account(LAN, AccountStatus.ACTIVE, null);
        when(accounts.findByEmail(LAN)).thenReturn(Optional.of(lan));

        service.signIn(SUB_1, LAN, "Lan Tran");

        assertThat(lan.getFullName()).isEqualTo("Lan Tran");
        assertThat(lan.getFirstSignInAt()).isEqualTo(T0);
        assertThat(lan.getLastSignInAt()).isEqualTo(T0);

        clock.set(T1);
        when(accounts.findByKeycloakSub(SUB_1)).thenReturn(Optional.of(lan));
        AccountSignInService.Result later = service.signIn(SUB_1, "lan.new@acme.example", "Lan T.");

        assertThat(later.status()).isEqualTo(AccountSignInService.Status.SIGNED_IN);
        assertThat(lan.getFullName()).isEqualTo("Lan T.");
        assertThat(lan.getFirstSignInAt()).isEqualTo(T0);
        assertThat(lan.getLastSignInAt()).isEqualTo(T1);
    }

    @Test
    @DisplayName(
            "edge 131 when the conditional bind changes no row the result is SUB_CONFLICT with one WARN naming only the id")
    void aLostBindIsASubConflict_edge131() {
        Account lan = account(LAN, AccountStatus.ACTIVE, null);
        when(accounts.findByEmail(LAN)).thenReturn(Optional.of(lan));
        when(accounts.bindKeycloakSub(lan.getId(), SUB_2)).thenReturn(0);

        AccountSignInService.Result result = service.signIn(SUB_2, LAN, "Lan Tran");

        assertThat(result.status()).isEqualTo(AccountSignInService.Status.SUB_CONFLICT);
        assertThat(result.accountId()).isEqualTo(lan.getId());
        assertThat(lan.getLastSignInAt()).isNull();
        verify(accounts, never()).save(any());
        assertThat(warnings()).hasSize(1);
        String message = warnings().get(0).getFormattedMessage();
        assertThat(message).contains(lan.getId().toString()).doesNotContain(LAN, SUB_1, SUB_2);
    }

    @ParameterizedTest(name = "[{index}] sub = \"{0}\"")
    @NullAndEmptySource
    @ValueSource(strings = {" ", "   ", "\t", " "})
    @DisplayName("AC-001.56 / I1 a null or blank sub is refused before any lookup: NO_ACCOUNT, nothing read or saved")
    void aNullOrBlankSubIsRefusedBeforeAnyLookup_AC_001_56(String sub) {
        AccountSignInService.Result result = service.signIn(sub, LAN, "Lan Tran");

        assertThat(result.status()).isEqualTo(AccountSignInService.Status.NO_ACCOUNT);
        assertThat(result.accountId()).isNull();
        verifyNoInteractions(accounts);
        assertThat(warnings()).isEmpty();
    }

    @Test
    @DisplayName(
            "edge 133 a sub of 65 characters is refused before any lookup: NO_ACCOUNT, nothing read, saved or logged")
    void aSubLongerThanTheColumnIsRefusedBeforeAnyLookup_edge133() {
        String tooLong = "s".repeat(65);

        AccountSignInService.Result result = service.signIn(tooLong, LAN, "Lan Tran");

        assertThat(result.status()).isEqualTo(AccountSignInService.Status.NO_ACCOUNT);
        assertThat(result.accountId()).isNull();
        verifyNoInteractions(accounts);
        assertThat(logs.list).extracting(ILoggingEvent::getFormattedMessage).noneMatch(m -> m.contains(tooLong));
    }

    @Test
    @DisplayName("edge 134 a sub of exactly 64 characters is still looked up and bound")
    void aSubOfExactlyTheColumnLengthIsAccepted_edge134() {
        String longest = "s".repeat(64);
        Account lan = account(LAN, AccountStatus.ACTIVE, null);
        when(accounts.findByEmail(LAN)).thenReturn(Optional.of(lan));

        AccountSignInService.Result result = service.signIn(longest, LAN, "Lan Tran");

        assertThat(result.status()).isEqualTo(AccountSignInService.Status.SIGNED_IN);
        verify(accounts).bindKeycloakSub(lan.getId(), longest);
    }

    @Test
    @DisplayName(
            "AC-001.56 / I3 when the unique index refuses the bind (the sub is held by another row) the result is SUB_CONFLICT with one WARN naming only the id")
    void aUniqueIndexViolationIsASubConflict_AC_001_56() {
        Account lan = account(LAN, AccountStatus.ACTIVE, null);
        when(accounts.findByEmail(LAN)).thenReturn(Optional.of(lan));
        when(accounts.bindKeycloakSub(lan.getId(), SUB_2))
                .thenThrow(new DataIntegrityViolationException(
                        "duplicate key value violates unique constraint \"uq_app_user_keycloak_sub\""));

        AccountSignInService.Result result = service.signIn(SUB_2, LAN, "Lan Tran");

        assertThat(result.status()).isEqualTo(AccountSignInService.Status.SUB_CONFLICT);
        assertThat(result.accountId()).isEqualTo(lan.getId());
        assertThat(lan.getKeycloakSub()).isNull();
        assertThat(lan.getLastSignInAt()).isNull();
        verify(accounts, never()).save(any());
        assertThat(warnings()).hasSize(1);
        String message = warnings().get(0).getFormattedMessage();
        assertThat(message).contains(lan.getId().toString()).doesNotContain(LAN, SUB_1, SUB_2);
    }

    @Test
    @DisplayName(
            "AC-001.13 / I2 when the bind changes no row because the Account was deactivated meanwhile the result is DEACTIVATED, not SUB_CONFLICT, and nothing is saved")
    void aBindLostToADeactivationIsDeactivated_AC_001_13() {
        Account stale = account(LAN, AccountStatus.ACTIVE, null);
        Account reloaded = account(LAN, AccountStatus.DEACTIVATED, null);
        ReflectionTestUtils.setField(reloaded, "id", stale.getId());
        // whichever way the service re-reads the row, it now sees the Deactivated state
        when(accounts.findByEmail(LAN)).thenReturn(Optional.of(stale), Optional.of(reloaded));
        when(accounts.findById(stale.getId())).thenReturn(Optional.of(reloaded));
        when(accounts.bindKeycloakSub(stale.getId(), SUB_1)).thenReturn(0);

        AccountSignInService.Result result = service.signIn(SUB_1, LAN, "Lan Tran");

        assertThat(result.status()).isEqualTo(AccountSignInService.Status.DEACTIVATED);
        assertThat(result.accountId()).isEqualTo(stale.getId());
        assertThat(reloaded.getKeycloakSub()).isNull();
        assertThat(stale.getKeycloakSub()).isNull();
        assertThat(stale.getLastSignInAt()).isNull();
        verify(accounts, never()).save(any());
        assertThat(warnings()).isEmpty();
    }

    @Test
    @DisplayName("AC-001.56 / I2 a successful sign-in never writes the whole Account row back with save")
    void aSignInDoesNotSaveTheWholeRow_AC_001_56() {
        Account lan = account(LAN, AccountStatus.ACTIVE, null);
        when(accounts.findByEmail(LAN)).thenReturn(Optional.of(lan));

        AccountSignInService.Result result = service.signIn(SUB_1, LAN, "Lan Tran");

        assertThat(result.status()).isEqualTo(AccountSignInService.Status.SIGNED_IN);
        verify(accounts, never()).save(any());
    }

    @Test
    @DisplayName("M1 the TransactionOperations is a required constructor argument, not an optional setter")
    void theTransactionOperationsIsAConstructorArgument_M1() {
        assertThat(AccountSignInService.class.getConstructors()).singleElement().satisfies(c -> assertThat(
                        c.getParameterTypes())
                .contains(org.springframework.transaction.support.TransactionOperations.class));
        assertThat(AccountSignInService.class.getDeclaredMethods())
                .extracting(java.lang.reflect.Method::getName)
                .doesNotContain("setTransactions");
    }

    @Test
    @DisplayName("M2 the keycloak_sub column is not updatable through the entity")
    void theSubColumnIsNotUpdatable_M2() throws Exception {
        jakarta.persistence.Column column =
                Account.class.getDeclaredField("keycloakSub").getAnnotation(jakarta.persistence.Column.class);

        assertThat(column.updatable()).isFalse();
    }

    @Test
    @DisplayName("M2 bindKeycloakSub on the entity is not public")
    void theEntityBindIsNotPublic_M2() throws Exception {
        int modifiers =
                Account.class.getDeclaredMethod("bindKeycloakSub", String.class).getModifiers();

        assertThat(java.lang.reflect.Modifier.isPublic(modifiers)).isFalse();
    }
}
