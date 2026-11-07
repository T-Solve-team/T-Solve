package vn.tsolve.search.list.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import vn.tsolve.common.security.Role;
import vn.tsolve.common.security.TSolveUser;
import vn.tsolve.common.security.WithTSolveUser;
import vn.tsolve.search.list.dto.TicketListQuery;
import vn.tsolve.search.list.dto.TicketSummaryResponse;
import vn.tsolve.search.list.service.TicketListService;
import vn.tsolve.search.scope.service.VisibleScope;

/** Exercises the real query, mapper, migrations and HTTP serialization against PostgreSQL. */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(
        properties = {
            "TSOLVE_LOGIN_ORIGIN=http://login.localhost:8081",
            "TSOLVE_ADMIN_ORIGIN=http://admin.localhost:8081",
            "TSOLVE_SIGNING_KEY=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
            "GOOGLE_CLIENT_ID=test-client-id",
            "GOOGLE_CLIENT_SECRET=test-secret",
            "PLATFORM_OPERATOR_EMAILS=ops@example.test",
            "CORS_ALLOWED_ORIGINS=http://localhost:5173",
            "server.port=0",
            "DB_HOST=unused",
            "DB_PORT=0",
            "DB_NAME=unused",
            "DB_USER=unused",
            "DB_PASSWORD=unused",
            "OLLAMA_BASE_URL=http://127.0.0.1:11434",
            "OLLAMA_CHAT_MODEL=test",
            "SEARCH_DEV_SCOPE="
        })
class TicketListRepositoryIT {
    @ServiceConnection
    static final PostgreSQLContainer<?> DB = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"));

    static {
        DB.start();
    }

    private static final TSolveUser USER =
            new TSolveUser(UUID.randomUUID(), "writer@example.test", "Writer", Set.of(Role.TICKET_RESOLVER));

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private TicketListService tickets;

    @Autowired
    private MockMvc mvc;

    @MockBean
    private VisibleScope scope;

    private final UUID departmentPublicId = UUID.randomUUID();
    private final UUID otherPublicId = UUID.randomUUID();
    private long department;
    private long managedProject;

    @BeforeEach
    void seed() {
        jdbc.execute("TRUNCATE ticket_import_decision, ticket, project, department CASCADE");
        department = department("IT", "ACTIVE", departmentPublicId);
        long other = department("HR", "ACTIVE", otherPublicId);
        long deleted = department("OLD", "DELETED", UUID.randomUUID());
        UUID project = project(department, "ITSUP");
        UUID managed = project(other, "HRHELP");
        managedProject = jdbc.queryForObject("SELECT project_id FROM project WHERE public_id = ?", Long.class, managed);
        UUID hiddenProject = project(other, "HRPRIVATE");
        ticket(project, "IT-1", "PUBLISHED", "someone", "VPN 100%_\\fix");
        ticket(project, "IT-2", "REJECTED", "writer", "Own solution");
        ticket(project, "IT-3", "PENDING_REVIEW", "someone", "Other writer");
        ticket(managed, "HR-1", "PENDING_REVIEW", "someone", "Managed ticket");
        ticket(managed, "HR-2", "PUBLISHED", "someone", "Not my Department");
        ticket(hiddenProject, "HR-3", "CHANGES_REQUESTED", "writer", "Own cross Department ticket");
        ticket(hiddenProject, "HR-4", "PUBLISHED", "writer", "Published outside scope");
        ticket(project(deleted, "OLD"), "OLD-1", "REJECTED", "writer", "Deleted Department");
        ticket(UUID.randomUUID(), "ORPHAN-1", "PUBLISHED", "writer", "No Project");
        when(scope.of(any()))
                .thenReturn(new VisibleScope.Scope(Set.of(department), Set.of(managedProject), Optional.of("writer")));
    }

    @Test
    void returnsOnlyVisibleTicketsAndHidesDeletedOrOrphaned_AC_006_1_BUG_140() {
        assertThat(list(null, null, null, null).stream().map(TicketSummaryResponse::sourceTicketId))
                .containsExactlyInAnyOrder("IT-1", "IT-2", "HR-1", "HR-3");
    }

    @Test
    void combinesFiltersWithLiteralKeyword_AC_006_4() {
        for (String keyword : new String[] {"%", "_", "\\fix", "vpn"}) {
            assertThat(list(keyword, departmentPublicId.toString(), "CSV", "PUBLISHED"))
                    .extracting(TicketSummaryResponse::sourceTicketId)
                    .containsExactly("IT-1");
        }
        assertThat(list("vpn", departmentPublicId.toString(), "MANUAL", "PUBLISHED"))
                .isEmpty();
    }

    @Test
    void outsideDepartmentExcludesOwnAndManagedTickets_AC_006_4() {
        assertThat(list(null, otherPublicId.toString(), null, null)).isEmpty();
        assertThat(list(null, UUID.randomUUID().toString(), null, null)).isEmpty();
    }

    @Test
    void returnsPublicIdsJiraProjectKeyAndNullReviewDate_AC_006_5_AC_006_6_AC_006_7() {
        var row = list("IT-1", null, null, null).getFirst();
        assertThat(row.ticketId())
                .isEqualTo(jdbc.queryForObject("SELECT public_id FROM ticket WHERE ticket_id='IT-1'", UUID.class));
        assertThat(row.project().name()).isEqualTo("ITSUP");
        assertThat(row.project().projectId())
                .isEqualTo(jdbc.queryForObject(
                        "SELECT public_id FROM project WHERE jira_project_key='ITSUP'", UUID.class));
        assertThat(row.department().departmentId()).isEqualTo(departmentPublicId);
        assertThat(row.department().name()).isEqualTo("IT");
        assertThat(row.reviewDueDate()).isNull();
        assertThat(row.updatedAt()).isEqualTo(Instant.parse("2026-11-01T00:00:00Z"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"sourceTicketId,asc", "title,desc", "project,asc", "updatedAt,desc", "reviewDueDate,asc"})
    void acceptsEverySortWithStablePaging_AC_006_3(String sort) {
        var first = tickets.list(USER, new TicketListQuery(null, null, null, null, 0, 2, sort));
        var second = tickets.list(USER, new TicketListQuery(null, null, null, null, 1, 2, sort));
        assertThat(first.getTotalElements()).isEqualTo(4);
        assertThat(first.getContent()).hasSize(2);
        assertThat(second.getContent()).hasSize(2).doesNotContainAnyElementsOf(first.getContent());
        List<String> expected =
                switch (sort) {
                    case "sourceTicketId,asc" -> List.of("HR-1", "HR-3", "IT-1", "IT-2");
                    case "title,desc" -> List.of("IT-1", "IT-2", "HR-3", "HR-1");
                    case "project,asc" -> List.of("HR-1", "HR-3", "IT-2", "IT-1");
                    default -> List.of("HR-3", "HR-1", "IT-2", "IT-1");
                };
        assertThat(java.util.stream.Stream.concat(first.stream(), second.stream())
                        .map(TicketSummaryResponse::sourceTicketId)
                        .toList())
                .isEqualTo(expected);
    }

    @Test
    @WithTSolveUser(roles = Role.TICKET_RESOLVER)
    void servesRealDatabaseThroughFrontendContract_AC_006_5_AC_006_10() throws Exception {
        mvc.perform(get("/api/v1/tickets").param("keyword", "IT-1").param("workspaceId", "ignored"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].sourceTicketId").value("IT-1"))
                .andExpect(jsonPath("$.content[0].project.name").value("ITSUP"))
                .andExpect(jsonPath("$.content[0].reviewDueDate").isEmpty())
                .andExpect(jsonPath("$.page.totalElements").value(1));
    }

    private List<TicketSummaryResponse> list(String keyword, String departmentId, String source, String status) {
        return tickets.list(USER, new TicketListQuery(keyword, departmentId, source, status, null, null, null))
                .getContent();
    }

    private long department(String name, String status, UUID id) {
        return jdbc.queryForObject(
                "INSERT INTO department(name,status,public_id) VALUES (?,?,?) RETURNING department_id",
                Long.class,
                name,
                status,
                id);
    }

    private UUID project(long departmentId, String key) {
        return jdbc.queryForObject(
                "INSERT INTO project(department_id,jira_project_key) VALUES (?,?) RETURNING public_id",
                UUID.class,
                departmentId,
                key);
    }

    private void ticket(UUID projectId, String key, String status, String writer, String title) {
        jdbc.update(
                """
                INSERT INTO ticket(ticket_id, project_id, source, status, title, writer,
                    symptoms, root_cause, resolution_steps, scope, updated_at)
                VALUES (?,?,'CSV',?,?,?,'Symptoms','Root cause','Resolution','Scope', '2026-11-01T00:00:00Z')
                """,
                key,
                projectId,
                status,
                title,
                writer);
    }
}
