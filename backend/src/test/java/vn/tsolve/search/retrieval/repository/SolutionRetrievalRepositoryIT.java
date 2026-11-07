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
    void returnsNearestAboveCutOff_AC_010_1_BUG_153() {
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
                .containsExactlyElementsOf(jdbc.queryForList(
                        "SELECT symptoms FROM ticket WHERE symptoms IN ('IT', 'HR') ORDER BY ticket_id", String.class));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM search_log", Integer.class))
                .isZero();
    }

    @Test
    void returnsNearestOfTheProjectsDepartment_AC_011_1() {
        long department = department("IT", "ACTIVE");
        long project = project(department, "ITA");
        long sibling = project(department, "ITB");
        UUID first = ticket(project, "FIRST", "PUBLISHED", 1, 0);
        UUID tie = ticket(sibling, "TIE", "PUBLISHED", 1, 0);
        UUID next = ticket(project, "NEXT", "PUBLISHED", 0.8f, 0.6f);
        ticket(project, "FAR", "PUBLISHED", 0, 1);

        // Equal similarity: the internal ticket id breaks the tie; the public id is what comes back.
        List<UUID> tieOrder = jdbc.queryForList(
                "SELECT public_id FROM ticket WHERE public_id IN (?, ?) ORDER BY ticket_id", UUID.class, first, tie);
        assertThat(repository.nearestInProjectDepartment(project, vector(1, 0), 0.7, 2))
                .extracting(SolutionRetrievalRepository.TicketSolutionRow::getTicketId)
                .containsExactlyElementsOf(tieOrder);
        var rows = repository.nearestInProjectDepartment(project, vector(1, 0), 0.7, 5);
        assertThat(rows)
                .extracting(SolutionRetrievalRepository.TicketSolutionRow::getTicketId)
                .containsExactly(tieOrder.get(0), tieOrder.get(1), next);
        var top = rows.getFirst();
        assertThat(top.getSimilarity()).isEqualTo(1);
        assertThat(top.getRootCause()).isEqualTo("Root cause");
        assertThat(top.getResolutionSteps()).isEqualTo("Resolution");
        assertThat(top.getWorkaround()).isBlank();
        assertThat(top.getScopeOfApplicability()).isEqualTo("Scope");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM search_log", Integer.class))
                .isZero();
    }

    @Test
    void leavesOutOtherDepartmentsUnpublishedInactiveOrFar_AC_011_3() {
        long project = project(department("IT", "ACTIVE"), "ITA");
        ticket(project, "UNPUBLISHED", "UNPUBLISHED", 1, 0);
        ticket(project, "FAR", "PUBLISHED", 0, 1);
        ticket(project(department("HR", "ACTIVE"), "HRA"), "OTHER", "PUBLISHED", 1, 0);
        long deleted = project(department("OLD", "DELETED"), "OLDA");
        ticket(deleted, "INACTIVE", "PUBLISHED", 1, 0);

        assertThat(repository.nearestInProjectDepartment(project, vector(1, 0), 0.7, 5))
                .isEmpty();
        assertThat(repository.nearestInProjectDepartment(deleted, vector(1, 0), 0.7, 5))
                .isEmpty();
        assertThat(repository.nearestInProjectDepartment(Long.MAX_VALUE, vector(1, 0), 0.7, 5))
                .isEmpty();
    }

    private long department(String name, String status) {
        return jdbc.queryForObject(
                "INSERT INTO department(name, status) VALUES (?, ?) RETURNING department_id", Long.class, name, status);
    }

    private long project(long department, String key) {
        return jdbc.queryForObject(
                "INSERT INTO project(department_id, jira_project_key) VALUES (?, ?) RETURNING project_id",
                Long.class,
                department,
                key);
    }

    private UUID ticket(long project, String key, String status, float x, float y) {
        UUID projectId = jdbc.queryForObject("SELECT public_id FROM project WHERE project_id = ?", UUID.class, project);
        var ids = jdbc.queryForMap(
                """
                INSERT INTO ticket(source_ticket_id, project_id, source, status, title,
                                   symptoms, root_cause, resolution_steps, workaround, scope)
                VALUES (?, ?, 'CSV', ?, 'Private title', ?, 'Root cause', 'Resolution', '  ', 'Scope')
                RETURNING ticket_id, public_id
                """,
                key,
                projectId,
                status,
                key);
        jdbc.update(
                "INSERT INTO ticket_embedding(ticket_id, embedding, model) VALUES (?, CAST(? AS vector), 'bge-m3')",
                ids.get("ticket_id"),
                vector(x, y));
        return (UUID) ids.get("public_id");
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
        UUID project = jdbc.queryForObject(
                "INSERT INTO project(department_id, jira_project_key) VALUES (?, ?) RETURNING public_id",
                UUID.class,
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
                project,
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
