package vn.tsolve.search.scope.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import vn.tsolve.common.security.TSolveUser;
import vn.tsolve.search.scope.repository.ScopeMembershipRepository;

class MembershipVisibleScopeTest {
    private final ScopeMembershipRepository repository = mock(ScopeMembershipRepository.class);
    private final MembershipVisibleScope scope = new MembershipVisibleScope(repository);
    private final TSolveUser user = new TSolveUser(UUID.randomUUID(), "writer@example.test", "Writer Name", Set.of());

    @Test
    void readsMembershipAndUsesFullNameForStoredWriter_AC_006_2() {
        when(repository.activeDepartments(user.id())).thenReturn(Set.of(3L));
        when(repository.managedProjects(user.id())).thenReturn(Set.of(7L, 9L));
        when(repository.writer(user.fullName())).thenReturn(Optional.of("Writer Name"));

        var result = scope.of(user);

        assertThat(result.departmentIds()).containsExactly(3L);
        assertThat(result.managedProjectIds()).containsExactlyInAnyOrder(7L, 9L);
        assertThat(result.writer()).contains("Writer Name");
    }

    @Test
    void answersEmptyWithoutMembershipOrStoredWriter_AC_006_13() {
        assertThat(scope.of(user).isEmpty()).isTrue();
    }
}
