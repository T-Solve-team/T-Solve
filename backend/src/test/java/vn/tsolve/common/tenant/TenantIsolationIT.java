package vn.tsolve.common.tenant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.test.context.assertj.AssertableApplicationContext;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.orm.jpa.SharedEntityManagerCreator;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.filter.ForwardedHeaderFilter;
import vn.tsolve.common.exception.GlobalExceptionHandler;
import vn.tsolve.platform.config.TenantFilterConfig;

/**
 * Tenant isolation through the web resolver (Task 10b): {@code Host} → {@code TenantResolverFilter} → routing
 * {@code DataSource}, with the production configuration against two Workspace databases. The probe binds nothing
 * itself: its only Workspace is the one the filter bound.
 */
class TenantIsolationIT {

    private static final String JPA_PATH = "/test/isolation/jpa";
    private static final String NATIVE_PATH = "/test/isolation/native";

    private static TwoWorkspaceDatabases databases;

    @BeforeAll
    static void createDatabases() throws Exception {
        databases = TwoWorkspaceDatabases.create("iso");
    }

    @Test
    @DisplayName("AC-002.18 a request on a Workspace site (A, then B) reads only its row by a JPA query")
    void aSiteRequestReadsOnlyItsRow_byJpa_AC_002_18() {
        runner().run(context -> {
            Site site = Site.of(context);
            site.assertReadsOnly(get(JPA_PATH).header("Host", host(databases.a())), databases.a(), "A");
            site.assertReadsOnly(get(JPA_PATH).header("Host", host(databases.b())), databases.b(), "B");
        });
    }

    @Test
    @DisplayName("AC-002.18 a request on a Workspace site (A, then B) reads only its row by a native SQL query")
    void aSiteRequestReadsOnlyItsRow_byNativeQuery_AC_002_18() {
        runner().run(context -> {
            Site site = Site.of(context);
            site.assertReadsOnly(get(NATIVE_PATH).header("Host", host(databases.a())), databases.a(), "A");
            site.assertReadsOnly(get(NATIVE_PATH).header("Host", host(databases.b())), databases.b(), "B");
        });
    }

    @Test
    @DisplayName("edge 1 AC-002.3 a request on A's host naming B in X-Forwarded-Host, Forwarded or ?workspaceId reads"
            + " only A's row")
    void aSiteHostNamingTheOtherWorkspaceReadsOnlyItsRow_edge_1_AC_002_3() {
        runner().run(context -> {
            // As in production, so the ForwardedHeaderFilter of this chain is the one the application runs.
            assertThat(context.getEnvironment().getProperty("server.forward-headers-strategy"))
                    .isEqualTo("framework");
            Site site = Site.of(context);
            UUID a = databases.a();
            String hostOfB = host(databases.b());
            String idOfB = databases.b().toString();

            for (String path : List.of(JPA_PATH, NATIVE_PATH)) {
                // serverName shows the forwarded host did take effect, and the resolver still ignored it
                site.assertReadsOnly(get(path).header("Host", host(a)).header("X-Forwarded-Host", hostOfB), a, "A")
                        .andExpect(jsonPath("$.serverName").value(hostOfB));
                site.assertReadsOnly(get(path).header("Host", host(a)).header("Forwarded", "host=" + hostOfB), a, "A")
                        .andExpect(jsonPath("$.serverName").value(hostOfB));
                site.assertReadsOnly(
                        get(path)
                                .header("Host", host(a))
                                .queryParam("workspaceId", idOfB)
                                .header("X-Workspace-Id", idOfB),
                        a,
                        "A");
            }
        });
    }

    private static ApplicationContextRunner runner() {
        return RoutingRunner.routing(databases.routingVariables())
                .withUserConfiguration(TenantFilterConfig.class)
                .withConfiguration(AutoConfigurations.of(JacksonAutoConfiguration.class))
                .withPropertyValues("TSOLVE_SITE_ORIGIN_TEMPLATE=https://{slug}.tsolve.test");
    }

    private static String host(UUID id) {
        return databases.siteName(id) + ".tsolve.test";
    }

    /** The probe behind the production filter chain of a started routing context. */
    private record Site(MockMvc mvc, ThreadLocalTenantContext holder) {

        static Site of(AssertableApplicationContext context) {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(javax.sql.DataSource.class))
                    .as("the primary DataSource routes by Workspace")
                    .isInstanceOf(TenantRoutingDataSource.class);
            var transaction =
                    new TransactionTemplate(context.getBean("transactionManager", PlatformTransactionManager.class));
            transaction.setReadOnly(true);
            var probe = new IsolationProbeController(
                    SharedEntityManagerCreator.createSharedEntityManager(context.getBean(EntityManagerFactory.class)),
                    transaction);
            var resolver = context.getBean("tenantResolverRegistration", FilterRegistrationBean.class)
                    .getFilter();
            MockMvc mvc = MockMvcBuilders.standaloneSetup(probe)
                    // production order: ForwardedHeaderFilter at HIGHEST_PRECEDENCE, then the resolver
                    .addFilters(new ForwardedHeaderFilter(), resolver)
                    .setControllerAdvice(new GlobalExceptionHandler())
                    .build();
            return new Site(mvc, context.getBean(ThreadLocalTenantContext.class));
        }

        ResultActions assertReadsOnly(MockHttpServletRequestBuilder request, UUID id, String note) throws Exception {
            ResultActions answer = mvc.perform(request)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.notes", containsInAnyOrder("probe", note)))
                    .andExpect(jsonPath("$.database").value(databases.database(id).database));
            assertThat(holder.currentWorkspaceId()).as("nothing is left bound").isEmpty();
            return answer;
        }
    }

    record ProbeAnswer(List<String> notes, String database, String serverName) {}

    @RestController
    static class IsolationProbeController {

        private final EntityManager em;
        private final TransactionTemplate transaction;

        IsolationProbeController(EntityManager em, TransactionTemplate transaction) {
            this.em = em;
            this.transaction = transaction;
        }

        @GetMapping(JPA_PATH)
        ProbeAnswer byJpa(HttpServletRequest request) {
            return read(request, () -> em.createQuery("SELECT p.note FROM IsolationProbe p", String.class)
                    .getResultList());
        }

        @GetMapping(NATIVE_PATH)
        ProbeAnswer byNativeQuery(HttpServletRequest request) {
            return read(request, () -> {
                List<?> rows = em.createNativeQuery("SELECT note FROM isolation_probes")
                        .getResultList();
                return rows.stream().map(String::valueOf).toList();
            });
        }

        private ProbeAnswer read(HttpServletRequest request, Supplier<List<String>> notes) {
            return transaction.execute(tx -> new ProbeAnswer(
                    notes.get(),
                    (String) em.createNativeQuery("SELECT current_database()").getSingleResult(),
                    request.getServerName()));
        }
    }
}
