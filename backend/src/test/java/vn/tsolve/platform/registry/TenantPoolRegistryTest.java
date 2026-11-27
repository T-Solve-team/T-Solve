package vn.tsolve.platform.registry;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.sql.DataSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.assertj.AssertableApplicationContext;
import org.springframework.core.io.ClassPathResource;
import org.yaml.snakeyaml.Yaml;
import vn.tsolve.common.tenant.RoutingRunner;
import vn.tsolve.common.tenant.TenantAwareJpaTransactionManager;
import vn.tsolve.common.tenant.TenantDataSources;
import vn.tsolve.common.tenant.TenantRoutingDataSource;

/** The connection budget of the staging stack and the gate of tenant routing (Task 6-2). No database is needed. */
class TenantPoolRegistryTest {

    private static final int WORKSPACES = 25;
    private static final int PROCESSES = 2; // backend and worker, routing on in both
    private static final int REGISTRY_POOL = 5; // RegistryDataSourceConfig.java:51
    // outbox pool 1 + 2 (OutboxOnlyConfiguration.java:64-65) and web-jobs pool 1 + 2 (WebJobsConfiguration.java:36,
    // JobRunrRuntime.java:58)
    private static final int WEB_JOBS = 6;
    private static final int WORKER_JOBS_ON_TOP_OF_WORKERS = 2; // JobRunrRuntime.java:58
    // TenantMigrator's one connection at startup (web only); counted per process as headroom
    private static final int MIGRATION_PER_PROCESS = 1;
    private static final int RESERVED_FOR_SUPERUSERS = 3; // PostgreSQL default, not set in compose

    private static final String KEY = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=";
    private static final String REGISTRY_PASSWORD = "fake-registry-password";
    private static final String DB_PASSWORD = "fake-db-password";

    @Test
    @DisplayName("edge 15 25 Workspaces in backend and worker stay below max_connections")
    void connectionBudgetStaysBelowMaxConnections_edge_15() throws IOException {
        Map<String, Object> compose =
                new Yaml().load(Files.readString(Path.of("../infra/compose.staging.yml"), StandardCharsets.UTF_8));
        Map<?, ?> services = (Map<?, ?>) compose.get("services");
        List<?> postgresCommand = (List<?>) ((Map<?, ?>) services.get("db")).get("command");
        Map<?, ?> keycloak = (Map<?, ?>) ((Map<?, ?>) services.get("keycloak")).get("environment");

        List<String> missing = new ArrayList<>();
        Integer maxConnections = number(
                "max_connections (infra/compose.staging.yml, db)",
                String.join("\n", postgresCommand.stream().map(String::valueOf).toList()),
                "(?m)^max_connections=(\\d+)$",
                missing);
        Integer keycloakPool = number(
                "KC_DB_POOL_MAX_SIZE (infra/compose.staging.yml, keycloak)",
                String.valueOf(keycloak.get("KC_DB_POOL_MAX_SIZE")),
                "^(\\d+)$",
                missing);
        Integer workers = number(
                "TSOLVE_WORKER_COUNT (infra/staging.env.example)",
                Files.readString(Path.of("../infra/staging.env.example"), StandardCharsets.UTF_8),
                "(?m)^TSOLVE_WORKER_COUNT=(\\d+)\\s*$",
                missing);
        Integer tenantPool = number(
                "TENANT_DB_POOL_MAX default (application.yml)",
                new String(
                        new ClassPathResource("application.yml")
                                .getInputStream()
                                .readAllBytes(),
                        StandardCharsets.UTF_8),
                "\\$\\{TENANT_DB_POOL_MAX:(\\d+)}",
                missing);
        assertThat(missing).as("values the connection budget needs").isEmpty();

        int total = WORKSPACES * tenantPool * PROCESSES
                + REGISTRY_POOL * PROCESSES
                + WEB_JOBS
                + workers
                + WORKER_JOBS_ON_TOP_OF_WORKERS
                + keycloakPool
                + MIGRATION_PER_PROCESS * PROCESSES;
        assertThat(total).isLessThanOrEqualTo(maxConnections - RESERVED_FOR_SUPERUSERS);
    }

    @Test
    @DisplayName("AC-002.20 AC-008.5 routing is on only with a Registry outside dev and then needs TENANT_DB_HOST")
    void routingOnlyWithRegistryOutsideDevAndNeedsHost_AC_002_20_AC_008_5() {
        Map<String, String> noRegistry = variables();
        noRegistry.remove("REGISTRY_DB_URL");
        RoutingRunner.configurationOnly(noRegistry).run(TenantPoolRegistryTest::assertRoutingOff);

        RoutingRunner.configurationOnly(variables())
                .withPropertyValues("spring.profiles.active=dev")
                .run(TenantPoolRegistryTest::assertRoutingOff);

        Map<String, String> noHost = variables();
        noHost.remove("TENANT_DB_HOST");
        RoutingRunner.routing(noHost).run(context -> {
            assertThat(context).hasFailed();
            StringWriter trace = new StringWriter();
            context.getStartupFailure().printStackTrace(new PrintWriter(trace));
            assertThat(trace.toString()).contains("TENANT_DB_HOST").doesNotContain(KEY, REGISTRY_PASSWORD, DB_PASSWORD);
        });

        RoutingRunner.routing(variables()).run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(DataSource.class)).isInstanceOf(TenantRoutingDataSource.class);
            assertThat(context.getBean("transactionManager")).isInstanceOf(TenantAwareJpaTransactionManager.class);
        });
    }

    private static void assertRoutingOff(AssertableApplicationContext context) {
        assertThat(context)
                .hasNotFailed()
                .doesNotHaveBean(TenantRoutingDataSource.class)
                .doesNotHaveBean(TenantDataSources.class)
                .doesNotHaveBean(TenantAwareJpaTransactionManager.class);
    }

    /** Hosts that do not exist: routing must start without opening any connection. */
    private static Map<String, String> variables() {
        Map<String, String> variables = new HashMap<>();
        variables.put("REGISTRY_DB_URL", "jdbc:postgresql://unused.invalid:5432/registry");
        variables.put("REGISTRY_DB_USER", "registry");
        variables.put("REGISTRY_DB_PASSWORD", REGISTRY_PASSWORD);
        variables.put("CATALOG_SECRET_KEY", KEY);
        variables.put("TENANT_DB_HOST", "unused.invalid");
        variables.put("DB_HOST", "unused.invalid");
        variables.put("DB_PORT", "5432");
        variables.put("DB_NAME", "app");
        variables.put("DB_USER", "app");
        variables.put("DB_PASSWORD", DB_PASSWORD);
        return variables;
    }

    private static Integer number(String name, String text, String pattern, List<String> missing) {
        Matcher matcher = Pattern.compile(pattern).matcher(text);
        if (matcher.find()) {
            return Integer.valueOf(matcher.group(1));
        }
        missing.add(name);
        return null;
    }
}
