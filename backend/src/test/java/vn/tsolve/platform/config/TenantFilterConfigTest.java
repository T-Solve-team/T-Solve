package vn.tsolve.platform.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.regex.MatchResult;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import vn.tsolve.common.tenant.KnownWorkspaces;
import vn.tsolve.common.tenant.ThreadLocalTenantContext;
import vn.tsolve.platform.service.WorkspaceCatalog;

/** Task 10a (KAN-258): what the resolver's wiring does at start-up when no base domain is configured. */
class TenantFilterConfigTest {

    /** An environment variable name: upper case with at least one underscore. */
    private static final Pattern VARIABLE_NAME = Pattern.compile("[A-Z][A-Z0-9]*(?:_[A-Z0-9]+)+");

    private final ApplicationContextRunner blankTemplate = new ApplicationContextRunner()
            .withUserConfiguration(TenantFilterConfig.class, ThreadLocalTenantContext.class)
            .withBean(KnownWorkspaces.class, () -> id -> false)
            .withBean(
                    ObjectMapper.class, () -> Jackson2ObjectMapperBuilder.json().build())
            .withPropertyValues("TSOLVE_SITE_ORIGIN_TEMPLATE=");

    @Test
    @DisplayName(
            "AC-002.2 edge 86 edge 91 a blank template fails the start-up only with a catalog; without one it logs one line")
    void failsStartupOnlyWithACatalogAndNoBaseDomain_AC_002_2() {
        blankTemplate
                .withBean(WorkspaceCatalog.class, () -> mock(WorkspaceCatalog.class))
                .run(context -> {
                    assertThat(context).hasFailed();
                    String reason = NestedExceptionUtils.getMostSpecificCause(context.getStartupFailure())
                            .getMessage();
                    assertThat(VARIABLE_NAME.matcher(reason).results().map(MatchResult::group))
                            .as("variables named by: " + reason)
                            .containsOnly("TSOLVE_SITE_ORIGIN_TEMPLATE");
                });

        Logger platform = (Logger) LoggerFactory.getLogger("vn.tsolve.platform");
        Level levelBefore = platform.getLevel();
        ListAppender<ILoggingEvent> lines = new ListAppender<>();
        lines.start();
        platform.setLevel(Level.INFO);
        platform.addAppender(lines);
        try {
            blankTemplate.run(context -> {
                assertThat(context).hasNotFailed();
                assertThat(List.copyOf(lines.list)).as("start-up log lines").hasSize(1);
            });
        } finally {
            platform.detachAppender(lines);
            platform.setLevel(levelBefore);
        }
    }
}
