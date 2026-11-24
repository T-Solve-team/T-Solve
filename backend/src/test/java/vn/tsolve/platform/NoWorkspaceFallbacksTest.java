package vn.tsolve.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.spy;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import liquibase.integration.spring.SpringLiquibase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import vn.tsolve.common.tenant.KnownWorkspaces;
import vn.tsolve.common.tenant.TenantContext;
import vn.tsolve.common.tenant.ThreadLocalTenantContext;
import vn.tsolve.worker.config.OutboxOnlyConfiguration;

/** Task 6a-2 (KAN-385): the Workspace fallbacks are gone, and nothing reads the names they used. */
class NoWorkspaceFallbacksTest {

    // The needles are built from pieces so that this file does not name them itself.
    private static final List<String> REMOVED_NAMES = List.of(
            "Stub" + "TenantContext",
            "Outbox" + "Workspace",
            "tsolve.stub." + "workspace-id",
            "TSOLVE_STUB_" + "WORKSPACE_ID",
            "tsolve.tracker." + "dev-workspace-id",
            "TSOLVE_TRACKER_" + "DEV_WORKSPACE_ID");

    @Test
    @DisplayName(
            "AC-002.13 with the removed stub property set, the only TenantContext bean is the holder and no stub or outbox Workspace bean exists")
    void theHolderIsTheOnlyTenantContextAndNoFallbackBeanExists_AC_002_13() {
        new ApplicationContextRunner()
                .withUserConfiguration(
                        ThreadLocalTenantContext.class, OutboxOnlyConfiguration.class, SkipMigration.class)
                .withBean(KnownWorkspaces.class, () -> id -> true)
                .withPropertyValues(
                        "tsolve.stub." + "workspace-id=" + new java.util.UUID(0, 1),
                        "tsolve.worker.outbox-enabled=true",
                        "tsolve.worker.jdbc-url=jdbc:postgresql://jobs.invalid:5432/jobs",
                        "tsolve.worker.password=p")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBeansOfType(TenantContext.class).values())
                            .singleElement()
                            .isInstanceOf(ThreadLocalTenantContext.class);
                    assertThat(context).doesNotHaveBean("stubTenantContext");
                    assertThat(context).doesNotHaveBean("outboxWorkspace");
                });
    }

    @Test
    @DisplayName("AC-002.13 the removed class and property names are read nowhere in the code, tests or env files")
    void theRemovedNamesAreReadNowhere_AC_002_13() throws IOException {
        List<Path> roots =
                List.of(Path.of("src/main"), Path.of("src/test"), Path.of("../.env.example"), Path.of("../infra"));
        List<String> hits;
        try (Stream<Path> files = roots.stream().flatMap(NoWorkspaceFallbacksTest::walk)) {
            hits = files.filter(Files::isRegularFile)
                    .flatMap(file -> REMOVED_NAMES.stream()
                            .filter(name -> contains(file, name))
                            .map(name -> file + " names " + name))
                    .toList();
        }

        assertThat(hits).as("files that still name a removed fallback").isEmpty();
    }

    private static Stream<Path> walk(Path root) {
        if (!Files.exists(root)) {
            return Stream.empty();
        }
        try {
            return Files.walk(root);
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }

    /** A whole-word match, so TSOLVE_STUB_WORKSPACES and TSOLVE_STUB_WORKSPACE_READY (kept) do not count. */
    private static boolean contains(Path file, String name) {
        try {
            return Pattern.compile("\\b" + Pattern.quote(name) + "\\b")
                    .matcher(Files.readString(file))
                    .find();
        } catch (IOException binaryOrUnreadable) {
            return false;
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class SkipMigration {
        @Bean
        static BeanPostProcessor skipMigration() {
            return new BeanPostProcessor() {
                @Override
                public Object postProcessBeforeInitialization(Object bean, String name) {
                    if (bean instanceof SpringLiquibase liquibase) {
                        var migration = spy(liquibase);
                        try {
                            doNothing().when(migration).afterPropertiesSet();
                        } catch (liquibase.exception.LiquibaseException failure) {
                            throw new IllegalStateException(failure);
                        }
                        return migration;
                    }
                    return bean;
                }
            };
        }
    }
}
