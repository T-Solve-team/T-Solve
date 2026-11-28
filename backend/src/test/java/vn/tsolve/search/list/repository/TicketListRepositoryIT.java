package vn.tsolve.search.list.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
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
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import vn.tsolve.common.security.Role;
import vn.tsolve.common.security.TSolveUser;
import vn.tsolve.common.security.WithTSolveUser;
import vn.tsolve.common.session.lookup.ProjectMembershipLookup;
import vn.tsolve.common.session.lookup.WorkspaceStatusLookup;
import vn.tsolve.common.tenant.KnownWorkspaces;
import vn.tsolve.identity.service.ScopeAccessService;
import vn.tsolve.identity.signin.port.PlatformWorkspaceLookup;
import vn.tsolve.identity.signin.port.TenantScope;
import vn.tsolve.identity.signin.port.WorkspaceLookup;
import vn.tsolve.jiraapp.service.JiraWorkspaceLookup;
import vn.tsolve.jiraapp.service.impl.CatalogJiraWorkspaceLookup;
import vn.tsolve.platform.service.WorkspaceCatalog;
import vn.tsolve.search.list.dto.TicketListQuery;
import vn.tsolve.search.list.dto.TicketSummaryResponse;
import vn.tsolve.search.list.service.TicketListService;
import vn.tsolve.search.scope.repository.ScopeTicketRepository;
import vn.tsolve.search.scope.service.VisibleScope;
import vn.tsolve.search.scope.service.impl.MembershipVisibleScope;
import vn.tsolve.workspace.service.impl.WorkspaceStatusLookupImpl;

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
    static final String WORKSPACE_ID = "6f1c2a3b-4d5e-4f60-8a7b-9c0d1e2f3a4b";

    @TestConfiguration(proxyBeanMethods = false)
    static class KnownWorkspaceConfig {
        @Bean
        @Primary
        KnownWorkspaces itWorkspace() {
            return id -> id.equals(UUID.fromString(WORKSPACE_ID));
        }
    }

    @ServiceConnection
    static final PostgreSQLContainer<?> DB = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"));

    static {
        DB.start();
    }

    private static final TSolveUser USER =
            new TSolveUser(UUID.randomUUID(), "writer@example.test", "Writer", Set.of(Role.TICKET_RESOLVER));

    @Autowired
    private ApplicationContext context;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private TicketListService tickets;

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ScopeTicketRepository scopeTickets;

    @Autowired
    private ScopeAccessService scopeAccess;

    @Autowired
    private ProjectMembershipLookup membershipLookup;

    @Autowired
    private TenantScope tenantScope;

    @MockBean
    private VisibleScope scope;

    private final UUID departmentPublicId = UUID.randomUUID();
    private final UUID otherPublicId = UUID.randomUUID();
    private long department;
    private long otherDepartment;
    private long managedProject;
    private long ticketSequence;
    private UUID otherWriter;
    private UUID itProject;
    private UUID managedProjectPublicId;

    @BeforeEach
    void seed() {
        ticketSequence = 0;
        jdbc.execute("TRUNCATE ticket_decision, ticket, project, department, app_user CASCADE");
        account(USER.id(), "writer@example.test", null);
        otherWriter = account(UUID.randomUUID(), "other@example.test", null);
        department = department("IT", "ACTIVE", departmentPublicId);
        long other = department("HR", "ACTIVE", otherPublicId);
        otherDepartment = other;
        long deleted = department("OLD", "DELETED", UUID.randomUUID());
        UUID project = project(department, "ITSUP");
        itProject = project;
        UUID managed = project(other, "HRHELP");
        managedProjectPublicId = managed;
        managedProject = jdbc.queryForObject("SELECT project_id FROM project WHERE public_id = ?", Long.class, managed);
        UUID hiddenProject = project(other, "HRPRIVATE");
        ticket(project, "IT-1", "PUBLISHED", null, "VPN 100%_\\fix");
        ticket(project, "IT-2", "REJECTED", USER.id(), "Own solution");
        ticket(project, "IT-3", "PENDING_REVIEW", otherWriter, "Other writer");
        ticket(managed, "HR-1", "PENDING_REVIEW", otherWriter, "Managed ticket");
        ticket(managed, "HR-2", "PUBLISHED", null, "Not my Department");
        ticket(hiddenProject, "HR-3", "CHANGES_REQUESTED", USER.id(), "Own cross Department ticket");
        ticket(hiddenProject, "HR-4", "PUBLISHED", USER.id(), "Published outside scope");
        ticket(hiddenProject, "HR-5", "PENDING_REVIEW", otherWriter, "Same writer name, other account");
        ticket(project(deleted, "OLD"), "OLD-1", "REJECTED", USER.id(), "Deleted Department");
        ticket(UUID.randomUUID(), "ORPHAN-1", "PUBLISHED", USER.id(), "No Project");
        when(scope.of(any()))
                .thenReturn(new VisibleScope.Scope(Set.of(department), Set.of(managedProject), Optional.of(USER.id())));
    }

    @Test
    void returnsOnlyVisibleTicketsAndHidesDeletedOrOrphaned_AC_006_1_BUG_153() {
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
    void keywordMatchesDescription_AC_006_4() {
        jdbc.update(
                "UPDATE ticket SET description = 'Token expired after password reset' WHERE source_ticket_id='IT-1'");
        assertThat(list("token EXPIRED", null, null, null))
                .extracting(TicketSummaryResponse::sourceTicketId)
                .containsExactly("IT-1");
    }

    @Test
    void outsideDepartmentExcludesOwnAndManagedTickets_AC_006_4() {
        assertThat(list(null, otherPublicId.toString(), null, null)).isEmpty();
        assertThat(list(null, UUID.randomUUID().toString(), null, null)).isEmpty();
    }

    @Test
    void returnsPublicIdsJiraProjectKeyAndNullReviewDate_AC_006_5_AC_006_6_AC_006_7_BUG_144() {
        var row = list("IT-1", null, null, null).getFirst();
        assertThat(row.ticketId())
                .isEqualTo(
                        jdbc.queryForObject("SELECT public_id FROM ticket WHERE source_ticket_id='IT-1'", UUID.class));
        assertThat(row.project().name()).isEqualTo("ITSUP");
        assertThat(row.project().projectId())
                .isEqualTo(jdbc.queryForObject(
                        "SELECT public_id FROM project WHERE jira_project_key='ITSUP'", UUID.class));
        assertThat(row.department().departmentId()).isEqualTo(departmentPublicId);
        assertThat(row.department().name()).isEqualTo("IT");
        assertThat(row.reviewDueDate()).isNull();
        assertThat(row.updatedAt()).isEqualTo(Instant.parse("2026-11-01T00:00:00Z"));
    }

    @Test
    void returnsAndSortsTheReviewDueDateOnlyWhileInReview_AC_006_7_BUG_401() {
        jdbc.update("UPDATE ticket SET review_due_date = '2026-11-27' WHERE source_ticket_id = 'HR-3'");
        jdbc.update("UPDATE ticket SET review_due_date = '2026-11-26' WHERE source_ticket_id = 'HR-1'");
        // A Published ticket keeps the column from its review; the list must not show it.
        jdbc.update("UPDATE ticket SET review_due_date = '2026-11-01' WHERE source_ticket_id = 'IT-1'");

        var rows = tickets.list(USER, new TicketListQuery(null, null, null, null, 0, 20, "reviewDueDate,asc"))
                .getContent();

        assertThat(rows)
                .extracting(TicketSummaryResponse::sourceTicketId, TicketSummaryResponse::reviewDueDate)
                .containsExactly(
                        tuple("HR-1", Instant.parse("2026-11-25T17:00:00Z")),
                        tuple("HR-3", Instant.parse("2026-11-26T17:00:00Z")),
                        tuple("IT-2", null),
                        tuple("IT-1", null));
    }

    @Test
    @org.junit.jupiter.api.DisplayName(
            "edge 83 the full context with the Registry gate off has one JiraWorkspaceLookup and one WorkspaceLookup, both catalog-backed and empty, and no WorkspaceCatalog")
    void gateOffContextHasOneCatalogLookupEach_edge83() {
        assertThat(context.getBeansOfType(WorkspaceCatalog.class)).isEmpty();
        assertThat(context.getBeansOfType(JiraWorkspaceLookup.class).values())
                .singleElement()
                .isInstanceOf(CatalogJiraWorkspaceLookup.class);
        assertThat(context.getBean(JiraWorkspaceLookup.class).workspaceOf("any"))
                .isEmpty();
        assertThat(context.getBeansOfType(WorkspaceLookup.class).values())
                .singleElement()
                .isInstanceOf(PlatformWorkspaceLookup.class);
        assertThat(context.getBeansOfType(WorkspaceStatusLookup.class).values())
                .singleElement()
                .isInstanceOf(WorkspaceStatusLookupImpl.class);
    }

    @Test
    void matchesTheWriterByAccountIdNotByName_AC_012_3() {
        // Every seeded ticket has the writer name "Writer"; only writer_id tells them apart.
        assertThat(list(null, null, null, null))
                .extracting(TicketSummaryResponse::sourceTicketId)
                .contains("IT-2", "HR-3")
                .doesNotContain("IT-3", "HR-5");
    }

    @Test
    void readsActiveDepartmentsAndProjectIds_AC_012_1_AC_012_2() {
        assertThat(scopeTickets.activeDepartmentRefs())
                .extracting(ref -> ref.getDepartmentId() + "=" + ref.getPublicId())
                .containsExactlyInAnyOrder(
                        department + "=" + departmentPublicId, otherDepartment + "=" + otherPublicId);
        assertThat(scopeTickets.projectIds(Set.of(managedProjectPublicId))).containsExactly(managedProject);
    }

    @Test
    void membershipScopeShowsTheDepartmentToMembersAndDraftsOnlyToThePmOrWriter_AC_012_1_AC_012_2_AC_012_4_BUG_323() {
        UUID resolver = account(UUID.randomUUID(), "resolver@example.test", null);
        role(resolver, "TICKET_RESOLVER");
        UUID manager = account(UUID.randomUUID(), "manager@example.test", department);
        role(manager, "PROJECT_MANAGER");
        UUID outsider = account(UUID.randomUUID(), "outsider@example.test", null);
        role(outsider, "TICKET_RESOLVER");
        membership(resolver, itProject, "MEMBER");
        membership(manager, managedProjectPublicId, "PROJECT_MANAGER");
        var membershipScope = new MembershipVisibleScope(scopeTickets, scopeAccess, membershipLookup);

        // A member of ITSUP sees the IT Department's Published ticket, but no draft of another writer.
        assertThat(visibleTo(membershipScope, resolver, Role.TICKET_RESOLVER)).containsExactly("IT-1");
        // The PM belongs to IT and manages HRHELP: the Published tickets of IT and HR, and the HRHELP draft.
        assertThat(visibleTo(membershipScope, manager, Role.PROJECT_MANAGER))
                .containsExactlyInAnyOrder("IT-1", "HR-1", "HR-2", "HR-4");
        // No membership: nothing (AC-012.4).
        assertThat(visibleTo(membershipScope, outsider, Role.TICKET_RESOLVER)).isEmpty();
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

    private List<String> visibleTo(MembershipVisibleScope membershipScope, UUID accountId, Role role) {
        var user = new TSolveUser(accountId, "user@example.test", "Writer", Set.of(role));
        doAnswer(call -> membershipScope.of(call.getArgument(0))).when(scope).of(any());
        return tenantScope.runIn(
                UUID.fromString(WORKSPACE_ID),
                () -> tickets.list(user, new TicketListQuery(null, null, null, null, null, null, null)).stream()
                        .map(TicketSummaryResponse::sourceTicketId)
                        .toList());
    }

    private UUID account(UUID id, String email, Long departmentId) {
        jdbc.update(
                "INSERT INTO app_user(id,email,full_name,status,created_at,department_id)"
                        + " VALUES (?,?,'Writer','ACTIVE',now(),?)",
                id,
                email,
                departmentId);
        return id;
    }

    private void role(UUID userId, String role) {
        jdbc.update("INSERT INTO user_role(user_id,role,assigned_at) VALUES (?,?,now())", userId, role);
    }

    private void membership(UUID userId, UUID projectId, String role) {
        jdbc.update(
                "INSERT INTO project_membership(user_id,project_id,role_in_project) VALUES (?,?,?)",
                userId,
                projectId,
                role);
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

    private void ticket(UUID projectId, String key, String status, UUID writerId, String title) {
        jdbc.update("""
                INSERT INTO ticket(ticket_id, source_ticket_id, project_id, source, status, title, writer, writer_id,
                    symptoms, root_cause, resolution_steps, scope, updated_at)
                VALUES (?,?,?,'CSV',?,?,'Writer',?,'Symptoms','Root cause','Resolution','Scope',
                    '2026-11-01T00:00:00Z')
                """, new UUID(0, ++ticketSequence), key, projectId, status, title, writerId);
    }
}
