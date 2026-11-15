package vn.tsolve.search.scope.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

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
    void readsMembershipAndUsesAccountIdForWriter_AC_006_2() {
        when(repository.activeDepartments(user.id())).thenReturn(Set.of(3L));
        when(repository.managedProjects(user.id())).thenReturn(Set.of(7L, 9L));

        var result = scope.of(user);

        assertThat(result.departmentIds()).containsExactly(3L);
        assertThat(result.managedProjectIds()).containsExactlyInAnyOrder(7L, 9L);
        assertThat(result.writerId()).contains(user.id());
    }

    @Test
    void answersWriterIdWithoutDepartmentOrProjectMembership_AC_006_13() {
        var result = scope.of(user);
        assertThat(result.departmentIds()).isEmpty();
        assertThat(result.managedProjectIds()).isEmpty();
        assertThat(result.writerId()).contains(user.id());
        assertThat(result.isEmpty()).isFalse();
    }
}
