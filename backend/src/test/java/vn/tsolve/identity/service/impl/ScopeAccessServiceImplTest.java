package vn.tsolve.identity.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;
import vn.tsolve.identity.exception.ScopeAccessDeniedException;
import vn.tsolve.identity.repository.ScopeAccessRepository;
import vn.tsolve.identity.signin.port.TenantScope;

class ScopeAccessServiceImplTest {
    private final TenantScope scope = mock(TenantScope.class);
    private final ScopeAccessRepository repository = mock(ScopeAccessRepository.class);
    private final ScopeAccessServiceImpl service = new ScopeAccessServiceImpl(scope, repository);
    private final UUID account = new UUID(118, 1);
    private final UUID target = new UUID(118, 2);

    @Test
    void refusesNullArgumentsWithoutReading_AC_011_8() {
        assertThat(service.canAccessDepartment(null, target)).isFalse();
        assertThat(service.canAccessDepartment(account, null)).isFalse();
        assertThat(service.canAccessProject(null, target)).isFalse();
        assertThat(service.canAccessProject(account, null)).isFalse();
        assertThat(service.canAccessSpace(null, target)).isFalse();
        assertThat(service.canAccessSpace(account, null)).isFalse();
        verifyNoInteractions(repository);
        verify(scope, times(6)).currentWorkspaceId();
    }

    @Test
    void propagatesUnboundWorkspaceBeforeCheckingEvenNullArguments_AC_011_9() {
        IllegalStateException failure = new IllegalStateException("No Workspace bound");
        when(scope.currentWorkspaceId()).thenThrow(failure);
        assertThatThrownBy(() -> service.canAccessDepartment(account, target)).isSameAs(failure);
        assertThatThrownBy(() -> service.canAccessProject(account, target)).isSameAs(failure);
        assertThatThrownBy(() -> service.canAccessSpace(account, target)).isSameAs(failure);
        assertThatThrownBy(() -> service.canAccessDepartment(null, null)).isSameAs(failure);
        assertThatThrownBy(() -> service.canAccessProject(null, null)).isSameAs(failure);
        assertThatThrownBy(() -> service.canAccessSpace(null, null)).isSameAs(failure);
        assertThatThrownBy(() -> service.requireDepartment(null, null)).isSameAs(failure);
        assertThatThrownBy(() -> service.requireProject(null, null)).isSameAs(failure);
        assertThatThrownBy(() -> service.requireSpace(null, null)).isSameAs(failure);
        verifyNoInteractions(repository);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void delegatesEachAnswerAfterCheckingWorkspace_AC_011_1(boolean allowed) {
        when(repository.department(account, target)).thenReturn(allowed);
        when(repository.project(account, target)).thenReturn(allowed);
        when(repository.space(account, target)).thenReturn(allowed);
        assertThat(service.canAccessDepartment(account, target)).isEqualTo(allowed);
        assertThat(service.canAccessProject(account, target)).isEqualTo(allowed);
        assertThat(service.canAccessSpace(account, target)).isEqualTo(allowed);
        var order = inOrder(scope, repository);
        order.verify(scope).currentWorkspaceId();
        order.verify(repository).department(account, target);
        order.verify(scope).currentWorkspaceId();
        order.verify(repository).project(account, target);
        order.verify(scope).currentWorkspaceId();
        order.verify(repository).space(account, target);
    }

    @Test
    void requiresAllowedScopesNormally_AC_011_1() {
        when(repository.department(account, target)).thenReturn(true);
        when(repository.project(account, target)).thenReturn(true);
        when(repository.space(account, target)).thenReturn(true);
        assertThatCode(() -> service.requireDepartment(account, target)).doesNotThrowAnyException();
        assertThatCode(() -> service.requireProject(account, target)).doesNotThrowAnyException();
        assertThatCode(() -> service.requireSpace(account, target)).doesNotThrowAnyException();
    }

    @Test
    void requiresEveryScopeWithForbiddenMessage_AC_011_10() {
        assertDenied(() -> service.requireDepartment(account, target));
        assertDenied(() -> service.requireProject(account, target));
        assertDenied(() -> service.requireSpace(account, target));
    }

    private void assertDenied(Runnable check) {
        assertThatThrownBy(check::run).isInstanceOfSatisfying(ScopeAccessDeniedException.class, error -> {
            assertThat(error.getCode()).isEqualTo("MSG08");
            assertThat(error.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
            assertThat(error.getParams()).isEmpty();
        });
    }
}
