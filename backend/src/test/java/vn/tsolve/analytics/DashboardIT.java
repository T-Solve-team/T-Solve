package vn.tsolve.analytics;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Parameter;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
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
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import vn.tsolve.analytics.controller.DashboardController;
import vn.tsolve.analytics.dto.KnowledgeDashboardResponse.DepartmentOption;
import vn.tsolve.analytics.dto.KnowledgeDashboardResponse.ProjectOption;
import vn.tsolve.analytics.repository.DashboardRepository;
import vn.tsolve.analytics.service.DashboardService;
import vn.tsolve.analytics.service.impl.DashboardServiceImpl;
import vn.tsolve.common.security.Role;
import vn.tsolve.common.security.TSolveUser;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({DashboardServiceImpl.class, DashboardRepository.class, DashboardIT.FixedClockConfig.class})
class DashboardIT {
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
    private UUID beta;
    private UUID zulu;
    private UUID inactive;
    private UUID activeProject;
    private UUID activeLowerProject;
    private UUID zuluProject;
    private UUID inactiveProject;
    private UUID inactiveDepartmentProject;
    private UUID manager;

    @BeforeEach
    void seed() {
        alpha = insertDepartment("Alpha", "ACTIVE");
        beta = insertDepartment("beta", "ACTIVE");
        zulu = insertDepartment("Zulu", "ACTIVE");
        inactive = insertDepartment("Hidden", "DEACTIVATED");
        activeProject = insertProject(alpha, "APP", "ACTIVE");
        activeLowerProject = insertProject(alpha, "app", "ACTIVE");
        zuluProject = insertProject(zulu, "ZZZ", "ACTIVE");
        inactiveProject = insertProject(alpha, "INACTIVE", "DEACTIVATED");
        inactiveDepartmentProject = insertProject(inactive, "HIDDEN", "ACTIVE");
        manager = insertUser(alpha);
    }

    @Test
    void adminOptionsContainOnlyActiveDepartmentsAndTheirActiveProjects_AC_004_13() {
        var result = service.dashboard(user(manager, Role.ADMIN), null, null, null, null);
        assertThat(result.options().departments())
                .extracting(DepartmentOption::departmentId)
                .containsExactly(alpha, beta, zulu);
        assertThat(result.options().departments())
                .extracting(DepartmentOption::name)
                .containsExactly("Alpha", "beta", "Zulu");
        assertThat(result.options().projects())
                .extracting(ProjectOption::projectId)
                .containsExactly(activeProject, activeLowerProject, zuluProject);
        assertThat(result.options().projects()).extracting(ProjectOption::name).containsExactly("APP", "app", "ZZZ");
        assertThat(result.options().projects())
                .extracting(ProjectOption::departmentId)
                .containsExactly(alpha, alpha, zulu);
    }

    @Test
    void managerOptionsIncludeOwnInactiveProjects_AC_004_13() {
        var result = service.dashboard(user(manager, Role.PROJECT_MANAGER), null, null, null, null);
        assertThat(result.options().departments()).containsExactly(new DepartmentOption(alpha, "Alpha"));
        assertThat(result.options().projects())
                .extracting(ProjectOption::projectId)
                .containsExactly(activeProject, activeLowerProject, inactiveProject);
        assertThat(result.options().projects())
                .allSatisfy(project -> assertThat(project.departmentId()).isEqualTo(alpha));
    }

    @Test
    void managerOptionsIncludeOwnInactiveDepartmentAndAllItsProjects_AC_004_13() {
        UUID inactiveInInactive = insertProject(inactive, "ARCHIVED", "DEACTIVATED");
        var result = service.dashboard(user(insertUser(inactive), Role.PROJECT_MANAGER), null, null, null, null);
        assertThat(result.options().departments()).containsExactly(new DepartmentOption(inactive, "Hidden"));
        assertThat(result.options().projects())
                .extracting(ProjectOption::projectId)
                .containsExactly(inactiveInInactive, inactiveDepartmentProject);
    }

    @Test
    void inactiveFilterIdsStillExist_AC_004_7() {
        var result = service.dashboard(
                user(manager, Role.ADMIN), null, null, inactive.toString(), inactiveDepartmentProject.toString());
        assertThat(result.period().to()).isEqualTo(java.time.LocalDate.of(2026, 11, 7));
        var projectResult = service.dashboard(
                user(manager, Role.PROJECT_MANAGER), null, null, alpha.toString(), inactiveProject.toString());
        assertThat(projectResult.options().projects())
                .extracting(ProjectOption::projectId)
                .contains(inactiveProject);
    }

    @Test
    void dashboardCallLeavesRowCountsUnchanged_AC_004_15() {
        List<Long> before = rowCounts();
        service.dashboard(user(manager, Role.ADMIN), null, null, null, null);
        service.dashboard(user(manager, Role.PROJECT_MANAGER), null, null, null, null);
        assertThat(rowCounts()).isEqualTo(before);
    }

    @Test
    void controllerDeclaresOnlyFourFiltersAndNoHeader_AC_004_15() throws Exception {
        var method = DashboardController.class.getDeclaredMethod(
                "dashboard", TSolveUser.class, String.class, String.class, String.class, String.class);
        var parameters = Arrays.stream(method.getParameters())
                .filter(p -> p.isAnnotationPresent(RequestParam.class))
                .toList();
        assertThat(parameters)
                .extracting(Parameter::getName)
                .containsExactly("from", "to", "departmentId", "projectId");
        assertThat(parameters).allSatisfy(parameter -> {
            assertThat(parameter.getType()).isEqualTo(String.class);
            assertThat(parameter.getAnnotation(RequestParam.class).required()).isFalse();
        });
        assertThat(method.getParameters()).noneMatch(parameter -> parameter.isAnnotationPresent(RequestHeader.class));
    }

    private List<Long> rowCounts() {
        return List.of(
                jdbc.queryForObject("select count(*) from department", Long.class),
                jdbc.queryForObject("select count(*) from project", Long.class),
                jdbc.queryForObject("select count(*) from app_user", Long.class));
    }

    private UUID insertDepartment(String name, String status) {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into department (public_id, name, status) values (?, ?, ?)", id, name, status);
        return id;
    }

    private UUID insertProject(UUID department, String key, String status) {
        UUID id = UUID.randomUUID();
        jdbc.update(
                "insert into project (public_id, department_id, jira_project_key, status) select ?, department_id, ?,"
                        + " ? from department where public_id = ?",
                id,
                key,
                status,
                department);
        return id;
    }

    private UUID insertUser(UUID department) {
        UUID id = UUID.randomUUID();
        jdbc.update(
                "insert into app_user (id, email, full_name, status, created_at, department_id) select ?, ?, 'Test"
                        + " User', 'ACTIVE', now(), department_id from department where public_id = ?",
                id,
                id + "@example.test",
                department);
        return id;
    }

    private TSolveUser user(UUID id, Role role) {
        return new TSolveUser(id, "user@example.test", "Test User", Set.of(role));
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
