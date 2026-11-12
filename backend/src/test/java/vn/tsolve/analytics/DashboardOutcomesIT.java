package vn.tsolve.analytics;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import vn.tsolve.analytics.dto.KnowledgeDashboardResponse;
import vn.tsolve.analytics.dto.KnowledgeDashboardResponse.Coverage;
import vn.tsolve.analytics.dto.KnowledgeDashboardResponse.DayOutcome;
import vn.tsolve.analytics.dto.KnowledgeDashboardResponse.DepartmentCoverage;
import vn.tsolve.analytics.dto.KnowledgeDashboardResponse.Outcomes;
import vn.tsolve.analytics.dto.KnowledgeDashboardResponse.ProjectOutcome;
import vn.tsolve.analytics.repository.DashboardRepository;
import vn.tsolve.analytics.service.DashboardService;
import vn.tsolve.analytics.service.impl.DashboardServiceImpl;
import vn.tsolve.common.security.Role;
import vn.tsolve.common.security.TSolveUser;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({DashboardServiceImpl.class, DashboardRepository.class, DashboardOutcomesIT.FixedClockConfig.class})
class DashboardOutcomesIT {
    private static final String OLD = "2026-09-01T00:00:00Z";
    private static final String START = "2026-10-31T17:00:00Z";
    private static final String BEFORE = "2026-10-31T16:59:59.999Z";
    private static final String LAST = "2026-11-03T16:59:59.999Z";
    private static final String END = "2026-11-03T17:00:00Z";

    @ServiceConnection
    static final PostgreSQLContainer<?> DB = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"));

    static {
        DB.start();
    }

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private DashboardService service;

    private UUID alpha;
    private UUID other;
    private UUID inactive;
    private UUID app;
    private UUID appLower;
    private UUID otherProject;
    private UUID inactiveProject;
    private UUID emptyProject;
    private UUID manager;

    @BeforeEach
    void seedScope() {
        alpha = insertDepartment("Alpha", "ACTIVE");
        other = insertDepartment("alpha", "ACTIVE");
        inactive = insertDepartment("Zulu", "DEACTIVATED");
        app = insertProject(alpha, "APP");
        appLower = insertProject(alpha, "app");
        otherProject = insertProject(other, "ZZZ");
        inactiveProject = insertProject(inactive, "HIDDEN");
        emptyProject = insertProject(alpha, "IDLE");
        manager = UUID.randomUUID();
        jdbc.update("""
                insert into app_user (id, email, full_name, status, created_at, department_id)
                select ?, ?, 'Test User', 'ACTIVE', now(), department_id from department where public_id = ?
                """, manager, manager + "@example.test", alpha);
    }

    @Test
    void countsDecisionsAndImportsPerDayAndProject_AC_004_8() {
        seedOutcomes();
        var result = dashboard(Role.ADMIN, null, null).outcomes();
        assertThat(result.approved()).isEqualTo(5);
        assertThat(result.rejected()).isEqualTo(3);
        assertThat(result.changesRequested()).isEqualTo(1);
        assertThat(result.approvalRate()).isEqualTo(5.0 / 9);
        assertThat(result.pendingReview()).isEqualTo(3);
        assertThat(result.expired()).isEqualTo(2);
        assertThat(result.perDay())
                .containsExactly(
                        new DayOutcome(LocalDate.of(2026, 11, 1), 2, 1, 0),
                        new DayOutcome(LocalDate.of(2026, 11, 2), 1, 1, 0),
                        new DayOutcome(LocalDate.of(2026, 11, 3), 2, 1, 1));
        assertThat(result.perProject())
                .containsExactly(
                        new ProjectOutcome(app, "APP", 3, 2, 1),
                        new ProjectOutcome(appLower, "app", 1, 0, 0),
                        new ProjectOutcome(otherProject, "ZZZ", 1, 1, 0));
    }

    @Test
    void restrictsDecisionsImportsAndCurrentStatusesToDepartmentAndProject_AC_004_8() {
        seedOutcomes();
        var department = dashboard(Role.ADMIN, alpha, null).outcomes();
        assertThat(department)
                .isEqualTo(new Outcomes(
                        4,
                        2,
                        1,
                        4.0 / 7,
                        2,
                        1,
                        List.of(
                                new DayOutcome(LocalDate.of(2026, 11, 1), 2, 1, 0),
                                new DayOutcome(LocalDate.of(2026, 11, 2), 0, 0, 0),
                                new DayOutcome(LocalDate.of(2026, 11, 3), 2, 1, 1)),
                        List.of(
                                new ProjectOutcome(app, "APP", 3, 2, 1),
                                new ProjectOutcome(appLower, "app", 1, 0, 0))));
        assertThat(dashboard(Role.PROJECT_MANAGER, null, null).outcomes()).isEqualTo(department);

        var project = dashboard(Role.ADMIN, null, app).outcomes();
        assertThat(project)
                .isEqualTo(new Outcomes(
                        3,
                        2,
                        1,
                        0.5,
                        1,
                        1,
                        List.of(
                                new DayOutcome(LocalDate.of(2026, 11, 1), 2, 1, 0),
                                new DayOutcome(LocalDate.of(2026, 11, 2), 0, 0, 0),
                                new DayOutcome(LocalDate.of(2026, 11, 3), 1, 1, 1)),
                        List.of(new ProjectOutcome(app, "APP", 3, 2, 1))));
        assertThat(dashboard(Role.PROJECT_MANAGER, alpha, app).outcomes()).isEqualTo(project);

        var empty = dashboard(Role.ADMIN, alpha, emptyProject).outcomes();
        assertThat(empty).isEqualTo(new Outcomes(0, 0, 0, null, 0, 0, emptyDays(1), List.of()));
    }

    @Test
    void ignoresSendBackDecisions_AC_004_8() {
        UUID sentBack = insertTicket(emptyProject, false, "SOLUTION_FORM", "UNPUBLISHED", OLD);
        insertDecision(sentBack, "SEND_BACK", START);
        assertThat(dashboard(Role.ADMIN, alpha, emptyProject).outcomes())
                .isEqualTo(new Outcomes(0, 0, 0, null, 0, 0, emptyDays(1), List.of()));
    }

    @Test
    void listsEveryEmptyDayAndKeepsCurrentCountsOutsidePeriod_AC_004_8() {
        seedOutcomes();
        var result = service.dashboard(user(Role.ADMIN), "2026-11-05", "2026-11-07", null, null);
        assertThat(result.outcomes()).isEqualTo(new Outcomes(0, 0, 0, null, 3, 2, emptyDays(5), List.of()));
    }

    @Test
    void countsCurrentCoverageByDepartmentIncludingDeactivatedDepartment_AC_004_9() {
        seedCoverage();
        var result = dashboard(Role.ADMIN, null, null);
        assertThat(result.coverage())
                .isEqualTo(new Coverage(
                        3,
                        6,
                        0.5,
                        List.of(
                                new DepartmentCoverage(alpha, "Alpha", 2, 3, 2.0 / 3),
                                new DepartmentCoverage(other, "alpha", 0, 1, 0.0),
                                new DepartmentCoverage(inactive, "Zulu", 1, 2, 0.5))));
        assertThat(result.options().departments())
                .extracting(KnowledgeDashboardResponse.DepartmentOption::departmentId)
                .doesNotContain(inactive);
        assertThat(service.dashboard(user(Role.ADMIN), "2026-11-07", "2026-11-07", null, null)
                        .coverage())
                .isEqualTo(result.coverage());
        assertThat(dashboard(Role.ADMIN, inactive, null).coverage())
                .isEqualTo(new Coverage(1, 2, 0.5, List.of(new DepartmentCoverage(inactive, "Zulu", 1, 2, 0.5))));
    }

    @Test
    void scopesCoverageToDepartmentAndPublicProjectIdIncludingLegacyTickets_AC_004_9() {
        seedCoverage();
        var department = new Coverage(2, 3, 2.0 / 3, List.of(new DepartmentCoverage(alpha, "Alpha", 2, 3, 2.0 / 3)));
        assertThat(dashboard(Role.ADMIN, alpha, null).coverage()).isEqualTo(department);
        assertThat(dashboard(Role.PROJECT_MANAGER, null, null).coverage()).isEqualTo(department);
        var project = new Coverage(2, 2, 1.0, List.of(new DepartmentCoverage(alpha, "Alpha", 2, 2, 1.0)));
        assertThat(dashboard(Role.ADMIN, null, app).coverage()).isEqualTo(project);
        assertThat(dashboard(Role.PROJECT_MANAGER, alpha, app).coverage()).isEqualTo(project);
        assertThat(dashboard(Role.ADMIN, alpha, emptyProject).coverage())
                .isEqualTo(new Coverage(0, 0, null, List.of()));
    }

    @Test
    void returnsNullShareWhenScopeHasNoTickets_AC_004_9() {
        assertThat(dashboard(Role.ADMIN, null, null).coverage()).isEqualTo(new Coverage(0, 0, null, List.of()));
        seedCoverage();
        assertThat(dashboard(Role.ADMIN, null, emptyProject).coverage()).isEqualTo(new Coverage(0, 0, null, List.of()));
        UUID emptyDepartment = insertDepartment("Empty", "ACTIVE");
        assertThat(dashboard(Role.ADMIN, emptyDepartment, null).coverage())
                .isEqualTo(new Coverage(0, 0, null, List.of()));
    }

    private void seedOutcomes() {
        UUID reviewed = insertTicket(app, false, "SOLUTION_FORM", "UNPUBLISHED", OLD);
        insertDecision(reviewed, "APPROVED", START);
        insertDecision(reviewed, "REJECTED", "2026-11-01T16:59:00Z");
        insertDecision(reviewed, "CHANGES_REQUESTED", "2026-11-02T17:00:00Z");
        insertDecision(reviewed, "APPROVED", LAST);
        for (String decision : List.of("APPROVED", "REJECTED", "CHANGES_REQUESTED")) {
            insertDecision(reviewed, decision, BEFORE);
            insertDecision(reviewed, decision, END);
        }
        insertTicket(app, false, "CSV", "PUBLISHED", START);
        insertTicket(appLower, false, "MANUAL", "PUBLISHED", LAST);
        insertTicket(otherProject, false, "JIRA_IMPORT", "UNPUBLISHED", "2026-11-01T17:00:00Z");
        for (String source : List.of("CSV", "MANUAL", "JIRA_IMPORT")) {
            insertTicket(app, false, source, "UNPUBLISHED", BEFORE);
            insertTicket(app, false, source, "UNPUBLISHED", END);
        }
        UUID legacy = insertTicket(app, true, "SOLUTION_FORM", "EXPIRED", OLD);
        insertDecision(legacy, "REJECTED", "2026-11-03T16:00:00Z");
        insertTicket(appLower, false, "SOLUTION_FORM", "PENDING_REVIEW", "2026-11-02T00:00:00Z");
        UUID otherTicket = insertTicket(otherProject, false, "SOLUTION_FORM", "PENDING_REVIEW", OLD);
        insertDecision(otherTicket, "REJECTED", "2026-11-02T00:00:00Z");
        insertTicket(app, false, "SOLUTION_FORM", "PENDING_REVIEW", OLD);
        insertTicket(otherProject, false, "SOLUTION_FORM", "EXPIRED", OLD);
    }

    private void seedCoverage() {
        insertTicket(app, false, "SOLUTION_FORM", "PUBLISHED", OLD);
        insertTicket(app, true, "CSV", "PUBLISHED", OLD);
        insertTicket(appLower, false, "SOLUTION_FORM", "REJECTED", OLD);
        insertTicket(otherProject, false, "SOLUTION_FORM", "PENDING_REVIEW", OLD);
        insertTicket(inactiveProject, false, "SOLUTION_FORM", "PUBLISHED", OLD);
        insertTicket(inactiveProject, true, "SOLUTION_FORM", "EXPIRED", OLD);
    }

    private KnowledgeDashboardResponse dashboard(Role role, UUID department, UUID project) {
        return service.dashboard(
                user(role),
                "2026-11-01",
                "2026-11-03",
                department == null ? null : department.toString(),
                project == null ? null : project.toString());
    }

    private List<DayOutcome> emptyDays(int firstDay) {
        return LocalDate.of(2026, 11, firstDay)
                .datesUntil(LocalDate.of(2026, 11, firstDay + 3))
                .map(day -> new DayOutcome(day, 0, 0, 0))
                .toList();
    }

    private UUID insertDepartment(String name, String status) {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into department (public_id, name, status) values (?, ?, ?)", id, name, status);
        return id;
    }

    private UUID insertProject(UUID department, String key) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                insert into project (public_id, department_id, jira_project_key, status)
                select ?, department_id, ?, 'ACTIVE' from department where public_id = ?
                """, id, key, department);
        return id;
    }

    private UUID insertTicket(UUID project, boolean legacy, String source, String status, String createdAt) {
        UUID id = UUID.randomUUID();
        UUID storedProject = legacy
                ? jdbc.queryForObject(
                        "select lpad(to_hex(project_id), 32, '0')::uuid from project where public_id = ?",
                        UUID.class,
                        project)
                : project;
        jdbc.update(
                """
                insert into ticket (ticket_id, public_id, source_ticket_id, project_id, source, status, title,
                    symptoms, root_cause, resolution_steps, scope, created_at)
                values (?, ?, ?, ?, ?, ?, 'Test ticket', 'Symptoms', 'Cause', 'Steps', 'Scope', ?)
                """,
                id,
                UUID.randomUUID(),
                id.toString(),
                storedProject,
                source,
                status,
                Timestamp.from(Instant.parse(createdAt)));
        return id;
    }

    private void insertDecision(UUID ticket, String decision, String decidedAt) {
        jdbc.update("""
                insert into review_decision (review_decision_id, ticket_id, project_manager_id, decision, decided_at)
                values (?, ?, ?, ?, ?)
                """, UUID.randomUUID(), ticket, manager, decision, Timestamp.from(Instant.parse(decidedAt)));
    }

    private TSolveUser user(Role role) {
        return new TSolveUser(manager, "user@example.test", "Test User", Set.of(role));
    }

    @TestConfiguration
    static class FixedClockConfig {
        @Bean
        Clock clock() {
            return Clock.fixed(Instant.parse("2026-11-06T18:00:00Z"), ZoneOffset.UTC);
        }

        @Bean
        NamedParameterJdbcTemplate namedParameterJdbcTemplate(JdbcTemplate jdbc) {
            return new NamedParameterJdbcTemplate(jdbc);
        }
    }
}
