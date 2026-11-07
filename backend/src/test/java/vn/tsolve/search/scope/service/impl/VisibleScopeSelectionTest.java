package vn.tsolve.search.scope.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.MapPropertySource;
import vn.tsolve.search.scope.ProjectIds;
import vn.tsolve.search.scope.repository.ScopeTicketRepository;
import vn.tsolve.search.scope.service.VisibleScope;

class VisibleScopeSelectionTest {
    private final ScopeTicketRepository repository = mock(ScopeTicketRepository.class);
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withBean(ScopeTicketRepository.class, () -> repository)
            .withUserConfiguration(Providers.class);

    @TestConfiguration(proxyBeanMethods = false)
    @Import({NoVisibleScope.class, DevAllVisibleScope.class})
    static class Providers {}

    @Test
    void failsClosedWhenUnsetEmptyOrOtherValue_AC_006_2_AC_006_12() {
        runner.run(context -> assertThat(
                        context.getBean(VisibleScope.class).of(null).isEmpty())
                .isTrue());
        for (String value : new String[] {"", "ALL", "true", "all "}) {
            runner.withInitializer(context -> context.getEnvironment()
                            .getPropertySources()
                            .addFirst(new MapPropertySource("scope-test", Map.of("SEARCH_DEV_SCOPE", value))))
                    .run(context -> {
                        assertThat(context.getEnvironment().getProperty("SEARCH_DEV_SCOPE"))
                                .isEqualTo(value);
                        assertThat(context).hasSingleBean(VisibleScope.class).doesNotHaveBean(DevAllVisibleScope.class);
                        assertThat(context.getBean(VisibleScope.class).of(null).isEmpty())
                                .isTrue();
                    });
        }
    }

    @Test
    void usesDevScopeOnlyForExactAll_AC_006_11_BUG_140() {
        when(repository.activeDepartments()).thenReturn(Set.of(1L));
        var firstProject = ProjectIds.ticketProjectId(7L);
        var secondProject = ProjectIds.ticketProjectId(9L);
        when(repository.projects()).thenReturn(Set.of(firstProject, secondProject));
        runner.withPropertyValues("SEARCH_DEV_SCOPE=all").run(context -> {
            assertThat(context).hasSingleBean(VisibleScope.class).doesNotHaveBean(NoVisibleScope.class);
            var scope = context.getBean(VisibleScope.class).of(null);
            assertThat(scope.departmentIds()).containsExactly(1L);
            assertThat(scope.managedProjectIds()).containsExactlyInAnyOrder(firstProject, secondProject);
            assertThat(scope.writer()).isEmpty();
        });
    }
}
