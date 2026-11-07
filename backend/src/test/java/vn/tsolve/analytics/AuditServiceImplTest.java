package vn.tsolve.analytics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Timestamp;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import vn.tsolve.common.audit.AuditAction;
import vn.tsolve.common.audit.impl.AuditServiceImpl;
import vn.tsolve.common.security.TSolveUser;

class AuditServiceImplTest {
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final AuditServiceImpl service = new AuditServiceImpl(jdbc, new ObjectMapper());
    private final UUID target = UUID.randomUUID();
    private final TSolveUser actor = new TSolveUser(UUID.randomUUID(), "pm@example.test", "Reviewer", Set.of());

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
        TransactionSynchronizationManager.clear();
    }

    @Test
    void refusesNoTransactionBeforeInserting_AC_002_2() {
        signIn();
        assertThatThrownBy(() -> service.record(AuditAction.APPROVE, "TICKET", target))
                .isInstanceOf(IllegalStateException.class);
        verifyNoInteractions(jdbc);
    }

    @Test
    void refusesNoActorBeforeInserting_AC_002_3() {
        TransactionSynchronizationManager.setActualTransactionActive(true);
        assertThatThrownBy(() -> service.record(AuditAction.APPROVE, "TICKET", target))
                .isInstanceOf(IllegalStateException.class);
        verifyNoInteractions(jdbc);
    }

    @Test
    void passesActorTargetAndJsonToJdbc_AC_002_1() {
        signIn();
        TransactionSynchronizationManager.setActualTransactionActive(true);
        service.record(AuditAction.IMPORT, "PROJECT", target, Map.of("created", 2));
        var json = ArgumentCaptor.forClass(String.class);
        verify(jdbc)
                .update(
                        any(String.class),
                        eq(actor.id()),
                        eq(actor.email()),
                        eq("IMPORT"),
                        eq("PROJECT"),
                        eq(target),
                        json.capture(),
                        any(Timestamp.class));
        assertThat(json.getValue()).isEqualTo("{\"created\":2}");
    }

    @Test
    void requiresNonNullTargetAndAction_AC_002_1() {
        signIn();
        TransactionSynchronizationManager.setActualTransactionActive(true);
        assertThatThrownBy(() -> service.record(null, "TICKET", target)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> service.record(AuditAction.APPROVE, null, target))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> service.record(AuditAction.APPROVE, "TICKET", null))
                .isInstanceOf(NullPointerException.class);
        verifyNoInteractions(jdbc);
    }

    private void signIn() {
        SecurityContextHolder.getContext()
                .setAuthentication(UsernamePasswordAuthenticationToken.authenticated(actor, null, actor.authorities()));
    }
}
