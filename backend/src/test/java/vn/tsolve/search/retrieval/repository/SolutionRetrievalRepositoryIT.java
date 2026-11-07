package vn.tsolve.search.retrieval.repository;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.persistence.EntityManager;
import java.sql.Connection;
import java.util.List;
import java.util.UUID;
import liquibase.Liquibase;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.ClassLoaderResourceAccessor;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.support.JpaRepositoryFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import vn.tsolve.search.embedding.repository.PgVector;

class SolutionRetrievalRepositoryIT {
    private static final PostgreSQLContainer<?> SERVER = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"));
    private static LocalContainerEntityManagerFactoryBean factory;
    private static JdbcTemplate jdbc;
    private EntityManager entityManager;
    private SolutionRetrievalRepository repository;

    @BeforeAll
    static void startServer() throws Exception {
        SERVER.start();
        var dataSource = new DriverManagerDataSource(SERVER.getJdbcUrl(), SERVER.getUsername(), SERVER.getPassword());
        jdbc = new JdbcTemplate(dataSource);
        try (Connection connection = dataSource.getConnection();
                ClassLoaderResourceAccessor resources = new ClassLoaderResourceAccessor();
                Liquibase liquibase = new Liquibase(
                        "db/changelog/db.changelog-master.xml",
                        resources,
                        DatabaseFactory.getInstance()
                                .findCorrectDatabaseImplementation(new JdbcConnection(connection)))) {
            liquibase.update("");
        }
        factory = new LocalContainerEntityManagerFactoryBean();
        factory.setDataSource(dataSource);
        factory.setPackagesToScan("vn.tsolve.search.scope.entity");
        factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
        factory.afterPropertiesSet();
    }

    @AfterAll
    static void stopServer() {
        if (factory != null) {
            factory.destroy();
        }
        SERVER.stop();
    }

    @BeforeEach
    void prepareRepository() {
        jdbc.execute("TRUNCATE ticket, project, department CASCADE");
        entityManager = factory.getObject().createEntityManager();
        repository = new JpaRepositoryFactory(entityManager).getRepository(SolutionRetrievalRepository.class);
    }

    @AfterEach
    void closeEntityManager() {
        entityManager.close();
    }

    @Test
    void returnsNearestAboveCutOff_AC_010_1() {
        insert("FIRST", "ACTIVE", "PUBLISHED", 1, 0);
        insert("TIE", "ACTIVE", "PUBLISHED", 1, 0);
        insert("NEXT", "ACTIVE", "PUBLISHED", 0.8f, 0.6f);
        insert("FAR", "ACTIVE", "PUBLISHED", 0, 1);

        // Equal similarity: the internal ticket id breaks the tie. It is a UUID, so read its order from the database.
        List<String> tieOrder = jdbc.queryForList(
                "SELECT symptoms FROM ticket WHERE symptoms IN ('FIRST', 'TIE') ORDER BY ticket_id", String.class);
        assertThat(nearest(0.7, 2))
                .extracting(SolutionRetrievalRepository.SolutionRow::getSymptoms)
                .containsExactlyElementsOf(tieOrder);
        assertThat(nearest(0.7, 5))
                .extracting(SolutionRetrievalRepository.SolutionRow::getSymptoms)
                .containsExactly(tieOrder.get(0), tieOrder.get(1), "NEXT");
        var first = nearest(1, 5).getFirst();
        assertThat(first.getSymptoms()).isEqualTo(tieOrder.getFirst());
        assertThat(first.getSimilarity()).isEqualTo(1);
        assertThat(first.getRootCause()).isEqualTo("Root cause");
        assertThat(first.getResolutionSteps()).isEqualTo("Resolution");
        assertThat(first.getWorkaround()).isBlank();
        assertThat(first.getScopeOfApplicability()).isEqualTo("Scope");
    }

    @Test
    void leavesOutUnpublishedInactiveOrFar_AC_010_3() {
        insert("UNPUBLISHED", "ACTIVE", "UNPUBLISHED", 1, 0);
        insert("INACTIVE", "DELETED", "PUBLISHED", 1, 0);
        insert("FAR", "ACTIVE", "PUBLISHED", 0, 1);
        assertThat(nearest(0.7, 5)).isEmpty();
    }

    @Test
    void ignoresTheUsersDepartments_AC_010_1() {
        insert("IT", "ACTIVE", "PUBLISHED", 1, 0);
        insert("HR", "ACTIVE", "PUBLISHED", 1, 0);
        assertThat(nearest(0.7, 5))
                .extracting(SolutionRetrievalRepository.SolutionRow::getSymptoms)
                .containsExactly("IT", "HR");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM search_log", Integer.class))
                .isZero();
    }

    private List<SolutionRetrievalRepository.SolutionRow> nearest(double minimum, int limit) {
        return repository.nearest(vector(1, 0), minimum, limit);
    }

    private void insert(String key, String departmentStatus, String ticketStatus, float x, float y) {
        Long department = jdbc.queryForObject(
                "INSERT INTO department(name, status) VALUES (?, ?) RETURNING department_id",
                Long.class,
                key,
                departmentStatus);
        Long project = jdbc.queryForObject(
                "INSERT INTO project(department_id, jira_project_key) VALUES (?, ?) RETURNING project_id",
                Long.class,
                department,
                key);
        UUID ticket = jdbc.queryForObject(
                """
                INSERT INTO ticket(source_ticket_id, project_id, source, status, title,
                                   symptoms, root_cause, resolution_steps, workaround, scope)
                VALUES (?, ?, 'CSV', ?, 'Private title', ?, 'Root cause', 'Resolution', '  ', 'Scope')
                RETURNING ticket_id
                """,
                UUID.class,
                key,
                new UUID(0, project),
                ticketStatus,
                key);
        jdbc.update(
                "INSERT INTO ticket_embedding(ticket_id, embedding, model) VALUES (?, CAST(? AS vector), 'bge-m3')",
                ticket,
                vector(x, y));
    }

    private static String vector(float x, float y) {
        float[] vector = new float[1024];
        vector[0] = x;
        vector[1] = y;
        return PgVector.literal(vector);
    }
}
