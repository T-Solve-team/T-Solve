package vn.tsolve.common.tenant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import com.zaxxer.hikari.HikariDataSource;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.LoggerFactory;
import org.springframework.boot.actuate.autoconfigure.health.HealthContributorAutoConfiguration;
import org.springframework.boot.actuate.autoconfigure.health.HealthEndpointAutoConfiguration;
import org.springframework.boot.actuate.autoconfigure.jdbc.DataSourceHealthContributorAutoConfiguration;
import org.springframework.boot.actuate.health.HealthEndpoint;
import org.springframework.boot.actuate.health.Status;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.assertj.AssertableApplicationContext;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.jpa.SharedEntityManagerCreator;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.tsolve.common.exception.GlobalExceptionHandler;

/** Tenant routing (Task 6-2) through the production configuration, against two Workspace databases. */
@ExtendWith(OutputCaptureExtension.class)
class TenantRoutingIT {

    private static final String CURRENT_DATABASE = "SELECT current_database()";
    private static final String TENANT_SETTING = "SELECT current_setting('app.tenant_id', true)";
    private static final String COUNT_PROBES = "SELECT count(*) FROM isolation_probes";
    private static final String NOTES = "SELECT note FROM isolation_probes";
    private static final String INSERT_PROBE = "INSERT INTO isolation_probes (note) VALUES ('written')";
    private static final String JPQL_NOTES = "SELECT p.note FROM IsolationProbe p";

    /** For the tests that neither change the databases nor assert that nothing was opened. */
    private static TwoWorkspaceDatabases shared;

    @BeforeAll
    static void createSharedDatabases() throws Exception {
        shared = TwoWorkspaceDatabases.create("rts");
    }

    @Test
    @DisplayName("AC-002.20 edge 87 JPA, native and JDBC queries of A reach A's database and B's reach B's")
    void queriesReachTheBoundWorkspacesDatabase_AC_002_20_edge_87() {
        runner(shared).run(context -> {
            Routed routed = Routed.of(context);
            for (UUID id : List.of(shared.a(), shared.b())) {
                String note = id.equals(shared.a()) ? "A" : "B";
                String database = shared.database(id).database;
                routed.holder.runIn(
                        id,
                        () -> routed.transaction.executeWithoutResult(tx -> {
                            assertThat(routed.em
                                            .createQuery(JPQL_NOTES, String.class)
                                            .getResultList())
                                    .containsExactlyInAnyOrder("probe", note);
                            assertThat(routed.em
                                            .createNativeQuery(CURRENT_DATABASE)
                                            .getSingleResult())
                                    .isEqualTo(database);
                            assertThat(routed.jdbc.queryForObject(CURRENT_DATABASE, String.class))
                                    .isEqualTo(database);
                        }));
            }
        });
    }

    @Test
    @DisplayName(
            "AC-002.22 edge 16 a pool opens on first use, is capped by TENANT_DB_POOL_MAX and two first uses share it")
    void poolOpensLazilyIsCappedAndShared_AC_002_22_edge_16() throws Exception {
        TwoWorkspaceDatabases databases = TwoWorkspaceDatabases.create("rtl");
        runner(databases).run(context -> {
            Routed routed = Routed.of(context);
            assertThat(databases.backends(databases.a()))
                    .as("backends of A after startup")
                    .isZero();

            ExecutorService threads = Executors.newFixedThreadPool(8);
            try {
                CountDownLatch start = new CountDownLatch(1);
                List<Future<DataSource>> firstUses = new ArrayList<>();
                for (int i = 0; i < 2; i++) {
                    firstUses.add(threads.submit(() -> {
                        start.await(10, TimeUnit.SECONDS);
                        return routed.port().poolOf(databases.a());
                    }));
                }
                start.countDown();
                DataSource first = firstUses.get(0).get(30, TimeUnit.SECONDS);
                assertThat(firstUses.get(1).get(30, TimeUnit.SECONDS)).isSameAs(first);
                assertThat(first).isInstanceOfSatisfying(HikariDataSource.class, pool -> {
                    assertThat(pool.getMaximumPoolSize()).isEqualTo(5);
                    assertThat(pool.getMinimumIdle()).isZero();
                    assertThat(pool.getIdleTimeout()).isEqualTo(600_000L);
                });

                List<Future<?>> queries = new ArrayList<>();
                for (int i = 0; i < 8; i++) {
                    // pg_sleep keeps the eight queries open at the same time, so the cap is what limits them
                    queries.add(threads.submit(() ->
                            routed.holder.runIn(databases.a(), () -> routed.jdbc.execute("SELECT pg_sleep(0.5)"))));
                }
                for (Future<?> query : queries) {
                    query.get(60, TimeUnit.SECONDS);
                }
                assertThat(databases.backends(databases.a())).isBetween(1, 5);
                assertThat(databases.backends(databases.b()))
                        .as("B was never used")
                        .isZero();
            } finally {
                threads.shutdownNow();
            }
        });
    }

    @Test
    @DisplayName("AC-002.23 AC-002.13 a transaction stays on its database and runIn of another Workspace inside it is "
            + "refused")
    void transactionStaysOnItsDatabase_AC_002_23() {
        runner(shared).run(context -> {
            Routed routed = Routed.of(context);
            String databaseOfA = shared.database(shared.a()).database;
            AtomicInteger ran = new AtomicInteger();
            AtomicInteger completion = new AtomicInteger(-1);

            routed.holder.runIn(
                    shared.a(),
                    () -> routed.transaction.executeWithoutResult(tx -> {
                        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                            @Override
                            public void afterCompletion(int outcome) {
                                completion.set(outcome);
                            }
                        });
                        assertThat(routed.jdbc.queryForObject(CURRENT_DATABASE, String.class))
                                .isEqualTo(databaseOfA);
                        Integer backend = routed.jdbc.queryForObject("SELECT pg_backend_pid()", Integer.class);

                        assertThatThrownBy(() -> routed.holder.runIn(shared.b(), (Runnable) ran::incrementAndGet))
                                .isInstanceOf(IllegalStateException.class);

                        assertThat(routed.jdbc.queryForObject(CURRENT_DATABASE, String.class))
                                .isEqualTo(databaseOfA);
                        assertThat(routed.jdbc.queryForObject("SELECT pg_backend_pid()", Integer.class))
                                .isEqualTo(backend);
                    }));

            assertThat(ran).hasValue(0);
            assertThat(completion).hasValue(TransactionSynchronization.STATUS_COMMITTED);
        });
    }

    @Test
    @DisplayName("AC-002.24 AC-002.25 edge 3 app.tenant_id is the bound id inside and outside a transaction and is "
            + "cleared after (pool of one)")
    void tenantSettingSetAndCleared_AC_002_24_edge_3() {
        runner(shared).withPropertyValues("TENANT_DB_POOL_MAX=1").run(context -> {
            Routed routed = Routed.of(context);
            String boundId = shared.a().toString();

            routed.holder.runIn(
                    shared.a(),
                    () -> routed.transaction.executeWithoutResult(tx -> {
                        assertThat(routed.jdbc.queryForObject(TENANT_SETTING, String.class))
                                .as("first statement of the transaction")
                                .isEqualTo(boundId);
                        assertThat(routed.jdbc.queryForObject(COUNT_PROBES, Integer.class))
                                .isEqualTo(2);
                    }));
            assertNoTenantSettingAndNoRow(routed.port().poolOf(shared.a()));

            routed.holder.runIn(shared.a(), () -> {
                assertThat(routed.jdbc.queryForObject(TENANT_SETTING, String.class))
                        .as("auto-commit statement")
                        .isEqualTo(boundId);
                assertThat(routed.jdbc.queryForObject(COUNT_PROBES, Integer.class))
                        .isEqualTo(2);
            });
            assertNoTenantSettingAndNoRow(routed.port().poolOf(shared.a()));

            assertThat(routed.port().poolOf(shared.a()))
                    .isInstanceOfSatisfying(HikariDataSource.class, pool -> assertThat(pool.getMaximumPoolSize())
                            .isEqualTo(1));
        });
    }

    @Test
    @DisplayName(
            "AC-002.25 edge 5 a connection misrouted to B's database while A is bound reads no row and writes none")
    void misrouteReadsAndWritesNothing_AC_002_25_edge_5() {
        runner(shared, Map.of(shared.a(), shared.b())).run(context -> {
            Routed routed = Routed.of(context);
            String databaseOfB = shared.database(shared.b()).database;

            routed.holder.runIn(shared.a(), () -> {
                Runnable reads = () -> {
                    assertThat(routed.jdbc.queryForObject(CURRENT_DATABASE, String.class))
                            .as("the misroute is in place")
                            .isEqualTo(databaseOfB);
                    assertThat(routed.em.createQuery(JPQL_NOTES, String.class).getResultList())
                            .isEmpty();
                    assertThat(routed.em.createNativeQuery(NOTES).getResultList())
                            .isEmpty();
                    assertThat(routed.jdbc.queryForList(NOTES, String.class)).isEmpty();
                };
                reads.run();
                routed.transaction.executeWithoutResult(tx -> reads.run());

                assertRefusedByRowPolicy(() -> routed.jdbc.update(INSERT_PROBE));
                assertRefusedByRowPolicy(
                        () -> routed.transaction.executeWithoutResult(tx -> routed.jdbc.update(INSERT_PROBE)));
                assertRefusedByRowPolicy(() -> routed.transaction.executeWithoutResult(tx -> {
                    routed.em.persist(new tsolvetest.tenant.IsolationProbe("written"));
                    routed.em.flush();
                }));
            });

            assertThat(shared.probeRows(shared.b())).isEqualTo(2);
        });
    }

    @Test
    @DisplayName(
            "AC-001.25 edge 13 edge 14 a wrong key for one Workspace refuses only that pool and logs its site name "
                    + "without the password")
    void wrongKeyRefusesOnlyThatPool_AC_001_25_edge_13_edge_14(CapturedOutput output) throws Exception {
        TwoWorkspaceDatabases databases = TwoWorkspaceDatabases.create("rtk");
        databases.reencryptWithOtherKey(databases.b());
        withDebugLogs(() -> runner(databases).run(context -> {
            Routed routed = Routed.of(context);

            assertRefusedWithoutSecrets(routed, databases, databases.b());
            assertAnswers500Msg06(routed, databases.b());
            assertThat(routed.databaseOf(databases.a())).isEqualTo(databases.database(databases.a()).database);

            assertThat(output.getAll())
                    .contains(databases.siteName(databases.b()))
                    .doesNotContain(databases.secrets());
        }));
    }

    @Test
    @DisplayName("edge 17 edge 14 one Workspace database down or the Registry down fails closed; the failure is not "
            + "cached")
    void oneDatabaseDownOrRegistryDownFailsClosed_edge_17_edge_14(CapturedOutput output) throws Exception {
        TwoWorkspaceDatabases databases = TwoWorkspaceDatabases.create("rtd");
        String databaseOfA = databases.database(databases.a()).database;
        String databaseOfB = databases.database(databases.b()).database;
        withDebugLogs(() -> {
            runner(databases).run(context -> {
                Routed routed = Routed.of(context);
                databases.allowConnections(databases.b(), false);
                try {
                    assertRefusedWithoutSecrets(routed, databases, databases.b());
                    assertAnswers500Msg06(routed, databases.b());
                    assertThat(routed.databaseOf(databases.a())).isEqualTo(databaseOfA);
                } finally {
                    databases.allowConnections(databases.b(), true);
                }
                assertThat(routed.databaseOf(databases.b()))
                        .as("B works again without a restart")
                        .isEqualTo(databaseOfB);
            });

            runner(databases).run(context -> {
                Routed routed = Routed.of(context);
                databases.allowRegistryConnections(false);
                try {
                    assertRefusedWithoutSecrets(routed, databases, databases.a());
                } finally {
                    databases.allowRegistryConnections(true);
                }
                assertThat(routed.databaseOf(databases.a()))
                        .as("A works once the Registry is back")
                        .isEqualTo(databaseOfA);
            });

            assertThat(output.getAll()).doesNotContain(databases.secrets());
        });
    }

    @Test
    @DisplayName(
            "AC-002.21 edge 23 edge 20 with nothing bound the context starts, health is UP without the router, and "
                    + "every query throws before a connection")
    void nothingBoundStartsHealthyAndRefusesQueries_AC_002_21_edge_23_edge_20() throws Exception {
        TwoWorkspaceDatabases databases = TwoWorkspaceDatabases.create("rtu");
        runner(databases)
                .withConfiguration(AutoConfigurations.of(
                        HealthContributorAutoConfiguration.class,
                        DataSourceHealthContributorAutoConfiguration.class,
                        HealthEndpointAutoConfiguration.class))
                .run(context -> {
                    Routed routed = Routed.of(context);
                    assertThat(context.getBean(HealthEndpoint.class)
                                    .healthForPath("db")
                                    .getStatus())
                            .isEqualTo(Status.UP);
                    assertThat(routed.port().calls())
                            .as("startup and health never ask for a pool")
                            .isZero();

                    assertThatThrownBy(() -> routed.router.getConnection())
                            .satisfies(TenantRoutingIT::assertCausedByNoTenant);
                    assertThatThrownBy(() -> routed.jdbc.update(INSERT_PROBE))
                            .satisfies(TenantRoutingIT::assertCausedByNoTenant);
                    assertThatThrownBy(() -> routed.transaction.executeWithoutResult(tx -> {
                                routed.em.persist(new tsolvetest.tenant.IsolationProbe("written"));
                                routed.em.flush();
                            }))
                            .satisfies(TenantRoutingIT::assertCausedByNoTenant);

                    assertThat(routed.port().calls()).isZero();
                    for (UUID id : List.of(databases.a(), databases.b())) {
                        assertThat(databases.backends(id)).isZero();
                        assertThat(databases.probeRows(id)).isEqualTo(2);
                    }
                });
    }

    // ---- helpers ----

    private static ApplicationContextRunner runner(TwoWorkspaceDatabases databases) {
        return runner(databases, Map.of());
    }

    private static ApplicationContextRunner runner(TwoWorkspaceDatabases databases, Map<UUID, UUID> misroutes) {
        return RoutingRunner.routing(databases.routingVariables())
                .withBean(
                        CountingTenantDataSources.Wrapping.class,
                        () -> new CountingTenantDataSources.Wrapping(misroutes));
    }

    /** The beans of a started routing context; fails first when routing is not in place. */
    private static final class Routed {

        private final AssertableApplicationContext context;
        final DataSource router;
        final ThreadLocalTenantContext holder;
        final JdbcTemplate jdbc;
        final EntityManager em;
        final TransactionTemplate transaction;

        private Routed(AssertableApplicationContext context) {
            this.context = context;
            router = context.getBean(DataSource.class);
            holder = context.getBean(ThreadLocalTenantContext.class);
            jdbc = context.getBean(JdbcTemplate.class);
            em = SharedEntityManagerCreator.createSharedEntityManager(context.getBean(EntityManagerFactory.class));
            transaction =
                    new TransactionTemplate(context.getBean("transactionManager", PlatformTransactionManager.class));
        }

        static Routed of(AssertableApplicationContext context) {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(DataSource.class))
                    .as("the primary DataSource routes by Workspace")
                    .isInstanceOf(TenantRoutingDataSource.class);
            return new Routed(context);
        }

        CountingTenantDataSources port() {
            return (CountingTenantDataSources) context.getBean(TenantDataSources.class);
        }

        String databaseOf(UUID id) {
            Supplier<String> query = () -> jdbc.queryForObject(CURRENT_DATABASE, String.class);
            return holder.runIn(id, query);
        }
    }

    private static void assertNoTenantSettingAndNoRow(DataSource pool) {
        try (Connection connection = pool.getConnection();
                Statement statement = connection.createStatement()) {
            try (ResultSet setting = statement.executeQuery(TENANT_SETTING)) {
                setting.next();
                assertThat(setting.getString(1))
                        .as("setting left on the connection")
                        .isNullOrEmpty();
            }
            try (ResultSet count = statement.executeQuery(COUNT_PROBES)) {
                count.next();
                assertThat(count.getInt(1)).as("rows read with no setting").isZero();
            }
        } catch (SQLException exception) {
            throw new AssertionError(exception);
        }
    }

    private static void assertRefusedByRowPolicy(Runnable write) {
        Throwable failure = catchThrowable(write::run);
        assertThat(failure).as("the write is refused").isNotNull();
        String sqlState = null;
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof SQLException sql) {
                sqlState = sql.getSQLState();
            }
        }
        assertThat(sqlState).isEqualTo("42501");
    }

    private static void assertCausedByNoTenant(Throwable failure) {
        List<Class<?>> chain = new ArrayList<>();
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            chain.add(cause.getClass());
        }
        assertThat(chain).contains(NoTenantException.class);
    }

    private static void assertRefusedWithoutSecrets(Routed routed, TwoWorkspaceDatabases databases, UUID id) {
        Throwable failure = catchThrowable(() -> routed.databaseOf(id));
        assertThat(failure).isInstanceOf(RuntimeException.class);
        StringWriter trace = new StringWriter();
        failure.printStackTrace(new PrintWriter(trace));
        assertThat(trace.toString()).doesNotContain(databases.secrets());
    }

    private static void assertAnswers500Msg06(Routed routed, UUID id) throws Exception {
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new QueryingController(() -> routed.databaseOf(id)))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
        mvc.perform(get("/test/database"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("MSG06"));
    }

    private interface Scenario {
        void run() throws Exception;
    }

    /** DEBUG carries the full exception chain of a handled failure, so "no password in a log" covers it too. */
    private static void withDebugLogs(Scenario scenario) throws Exception {
        List<Logger> loggers = List.of((Logger) LoggerFactory.getLogger("vn.tsolve.common.exception"), (Logger)
                LoggerFactory.getLogger("vn.tsolve.platform.registry"));
        List<Level> before = loggers.stream().map(Logger::getLevel).toList();
        loggers.forEach(logger -> logger.setLevel(Level.DEBUG));
        try {
            scenario.run();
        } finally {
            for (int i = 0; i < loggers.size(); i++) {
                loggers.get(i).setLevel(before.get(i));
            }
        }
    }

    @RestController
    static class QueryingController {
        private final Supplier<String> query;

        QueryingController(Supplier<String> query) {
            this.query = query;
        }

        @GetMapping("/test/database")
        String database() {
            return query.get();
        }
    }
}
