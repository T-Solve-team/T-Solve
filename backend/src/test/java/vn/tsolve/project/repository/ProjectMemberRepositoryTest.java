package vn.tsolve.project.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DeadlockLoserDataAccessException;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import vn.tsolve.common.exception.BusinessRuleException;

@ExtendWith(MockitoExtension.class)
class ProjectMemberRepositoryTest {
    private static final UUID PROJECT = UUID.randomUUID();
    private static final UUID A = UUID.randomUUID();

    @Mock
    private NamedParameterJdbcTemplate jdbc;

    @SuppressWarnings("unchecked")
    private void lockFails(RuntimeException failure) {
        when(jdbc.query(contains("FOR SHARE"), anyMap(), (RowMapper<UUID>) org.mockito.ArgumentMatchers.any()))
                .thenThrow(failure);
    }

    @Test
    @SuppressWarnings("unchecked")
    void locksAccountsInAscendingIdOrder_AC_002_7() {
        repository().lockAccounts(List.of(A));

        verify(jdbc)
                .query(
                        eq("SELECT id FROM app_user WHERE id IN (:ids) ORDER BY id FOR SHARE"),
                        eq(Map.of("ids", List.of(A))),
                        (RowMapper<UUID>) org.mockito.ArgumentMatchers.any());
    }

    @Test
    void answersConflictWhenAccountLockCannotBeAcquired_AC_002_7() {
        lockFails(new CannotAcquireLockException("lock timeout"));

        assertThatThrownBy(() -> repository().lockAccounts(List.of(A)))
                .isInstanceOfSatisfying(BusinessRuleException.class, e -> assertThat(e.getCode())
                        .isEqualTo("MSG06"));
    }

    @Test
    void answersConflictWhenAccountLockDeadlocks_AC_002_7() {
        lockFails(new DeadlockLoserDataAccessException("deadlock", null));

        assertThatThrownBy(() -> repository().lockEligible(PROJECT, List.of(A)))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void answersConflictWhenProjectLockCannotBeAcquired_AC_002_8() {
        lockFails(new CannotAcquireLockException("lock timeout"));

        assertThatThrownBy(() -> repository().lockProject(PROJECT)).isInstanceOf(BusinessRuleException.class);
    }

    private ProjectMemberRepository repository() {
        return new ProjectMemberRepository(jdbc);
    }
}
