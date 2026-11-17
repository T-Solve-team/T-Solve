package vn.tsolve.search.scope.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.MapPropertySource;
import vn.tsolve.common.session.lookup.ProjectMembershipLookup;
import vn.tsolve.identity.service.ScopeAccessService;
import vn.tsolve.search.scope.repository.ScopeTicketRepository;
import vn.tsolve.search.scope.service.VisibleScope;

class VisibleScopeSelectionTest {
    private final ScopeTicketRepository repository = mock(ScopeTicketRepository.class);
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withBean(ScopeTicketRepository.class, () -> repository)
            .withBean(ScopeAccessService.class, () -> mock(ScopeAccessService.class))
            .withBean(ProjectMembershipLookup.class, () -> mock(ProjectMembershipLookup.class))
            .withUserConfiguration(Providers.class);

    @TestConfiguration(proxyBeanMethods = false)
    @Import({MembershipVisibleScope.class, DevAllVisibleScope.class})
    static class Providers {}

    @Test
    void usesMembershipScopeWhenUnsetEmptyOrOtherValue_AC_006_2_AC_006_12_AC_012_6() {
        runner.run(context ->
                assertThat(context).hasSingleBean(VisibleScope.class).hasSingleBean(MembershipVisibleScope.class));
        for (String value : new String[] {"", "ALL", "true", "all "}) {
            runner.withInitializer(context -> context.getEnvironment()
                            .getPropertySources()
                            .addFirst(new MapPropertySource("scope-test", Map.of("SEARCH_DEV_SCOPE", value))))
                    .run(context -> {
                        assertThat(context.getEnvironment().getProperty("SEARCH_DEV_SCOPE"))
                                .isEqualTo(value);
                        assertThat(context)
                                .hasSingleBean(VisibleScope.class)
                                .hasSingleBean(MembershipVisibleScope.class)
                                .doesNotHaveBean(DevAllVisibleScope.class);
                    });
        }
    }

    @Test
    void theFailClosedStandInIsGone_AC_012_6() {
        assertThatThrownBy(() -> Class.forName("vn.tsolve.search.scope.service.impl.NoVisibleScope"))
                .isInstanceOf(ClassNotFoundException.class);
    }

    @Test
    void usesDevScopeOnlyForExactAll_AC_006_11() {
        when(repository.activeDepartments()).thenReturn(Set.of(1L));
        when(repository.projects()).thenReturn(Set.of(7L, 9L));
        runner.withPropertyValues("SEARCH_DEV_SCOPE=all").run(context -> {
            assertThat(context).hasSingleBean(VisibleScope.class).doesNotHaveBean(MembershipVisibleScope.class);
            var scope = context.getBean(VisibleScope.class).of(null);
            assertThat(scope.departmentIds()).containsExactly(1L);
            assertThat(scope.managedProjectIds()).containsExactlyInAnyOrder(7L, 9L);
            assertThat(scope.writerId()).isEmpty();
        });
    }
}
