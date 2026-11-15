package vn.tsolve.ticket.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import javax.sql.DataSource;
import liquibase.Contexts;
import liquibase.LabelExpression;
import liquibase.Liquibase;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.ClassLoaderResourceAccessor;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.util.ClassUtils;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.tsolve.identity.TwoTenantDatabases;

/** KAN-137: changesets 038 and 039 on a database that already holds review and import decisions. */
class TicketDecisionMigrationIT {

    private static final String CHANGELOG = "db/changelog/db.changelog-master.xml";
    private static final Timestamp REVIEWED_AT = Timestamp.from(Instant.parse("2026-11-01T03:00:00Z"));
    private static final Timestamp IMPORTED_AT = Timestamp.from(Instant.parse("2026-11-02T04:00:00Z"));

    private static JdbcTemplate jdbc;
    private static UUID reviewDecision;
    private static UUID reviewer;
    private static UUID formTicket;
    private static UUID csvTicket;

    @BeforeAll
    static void migrateASeededDatabase() throws Exception {
        DataSource dataSource = TwoTenantDatabases.emptyDatabase("ticket_decision_migration");
        jdbc = new JdbcTemplate(dataSource);
        try (var connection = dataSource.getConnection();
                var liquibase =
                        new Liquibase(CHANGELOG, new ClassLoaderResourceAccessor(), new JdbcConnection(connection))) {
            int before = 0;
            for (var change : liquibase.listUnrunChangeSets(new Contexts(), new LabelExpression())) {
                if (change.getId().equals("038-ticket-decision")) break;
                before++;
            }
            liquibase.update(before, new Contexts(), new LabelExpression());
            seed();
            liquibase.update(new Contexts(), new LabelExpression());
        }
    }

    private static void seed() {
        reviewer = jdbc.queryForObject(
                "insert into app_user (email, status, created_at) values ('pm@example.test', 'ACTIVE', now()) returning id",
                UUID.class);
        formTicket = ticket("FORM-1", "SOLUTION_FORM", "REJECTED");
        csvTicket = ticket("CSV-1", "CSV", "PUBLISHED");
        reviewDecision = jdbc.queryForObject("""
                insert into review_decision (ticket_id, project_manager_id, decision, reason_or_comment, decided_at)
                values (?, ?, 'REJECTED', 'Too vague', ?) returning review_decision_id
                """, UUID.class, formTicket, reviewer, REVIEWED_AT);
        jdbc.update("""
                insert into ticket_import_decision (ticket_id, reviewer, decision, reason, decided_at)
                values (?, 'pm-signin', 'APPROVE', 'From CSV', ?)
                """, csvTicket, IMPORTED_AT);
    }

    private static UUID ticket(String key, String source, String status) {
        return jdbc.queryForObject("""
                insert into ticket (source_ticket_id, project_id, source, status, title, symptoms, root_cause,
                                    resolution_steps, scope)
                values (?, '00000000-0000-0000-0000-000000000001', ?, ?, 'Login fails', 's', 'r', 'st', 'sc')
                returning ticket_id
                """, UUID.class, key, source, status);
    }

    private static int tables(String... names) {
        return jdbc.queryForObject(
                "select count(*) from information_schema.tables where table_schema = 'public' and table_name = any(?)",
                Integer.class,
                (Object) names);
    }

    @Test
    void hasOneDecisionTable_AC_010_1() {
        assertThat(tables("ticket_decision")).isOne();
        assertThat(tables("ticket_import_decision", "review_decision")).isZero();
    }

    @Test
    void keepsEveryReviewAndImportDecision_AC_010_2() {
        assertThat(jdbc.queryForMap(
                        "select ticket_decision_id, ticket_id, reviewer_id, reviewer, decision, feedback, decided_at"
                                + " from ticket_decision where ticket_id = ?",
                        formTicket))
                .containsEntry("ticket_decision_id", reviewDecision)
                .containsEntry("reviewer_id", reviewer)
                .containsEntry("reviewer", null)
                .containsEntry("decision", "REJECTED")
                .containsEntry("feedback", "Too vague")
                .containsEntry("decided_at", REVIEWED_AT);
        assertThat(jdbc.queryForMap(
                        "select reviewer_id, reviewer, decision, feedback, decided_at from ticket_decision"
                                + " where ticket_id = ?",
                        csvTicket))
                .containsEntry("reviewer_id", null)
                .containsEntry("reviewer", "pm-signin")
                .containsEntry("decision", "APPROVED")
                .containsEntry("feedback", "From CSV")
                .containsEntry("decided_at", IMPORTED_AT);
        assertThat(jdbc.queryForObject("select count(*) from ticket_decision", Integer.class))
                .isEqualTo(2);
    }

    @Test
    void rejectsADecisionWithoutReviewerOrWithAnUnknownValue_AC_010_6() {
        assertThatThrownBy(() -> jdbc.update(
                        "insert into ticket_decision (ticket_id, decision) values (?, 'APPROVED')", csvTicket))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update(
                        "insert into ticket_decision (ticket_id, reviewer, decision) values (?, 'pm', 'APPROVE')",
                        csvTicket))
                .isInstanceOf(DataIntegrityViolationException.class);
        for (String value :
                List.of("APPROVED", "REJECTED", "CHANGES_REQUESTED", "SEND_BACK", "UNPUBLISH", "REPUBLISH")) {
            jdbc.update(
                    "insert into ticket_decision (ticket_id, reviewer, decision) values (?, 'pm', ?)",
                    csvTicket,
                    value);
        }
        jdbc.update("delete from ticket_decision where reviewer = 'pm'");
    }

    @Test
    void hasNoLegacySolutionTableAndNoSolutionsEndpoint_AC_010_7() throws Exception {
        assertThat(tables(
                        "solution", "solution_source", "solution_approval", "resolved_ticket_snapshot", "reuse_record"))
                .isZero();
        var scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(RestController.class));
        for (var candidate : scanner.findCandidateComponents("vn.tsolve")) {
            Class<?> controller = ClassUtils.forName(candidate.getBeanClassName(), null);
            var mapping = AnnotatedElementUtils.findMergedAnnotation(controller, RequestMapping.class);
            if (mapping != null) {
                assertThat(mapping.path()).noneMatch(path -> path.startsWith("/api/solutions"));
            }
            for (var method : controller.getDeclaredMethods()) {
                var methodMapping = AnnotatedElementUtils.findMergedAnnotation(method, RequestMapping.class);
                if (methodMapping != null) {
                    assertThat(methodMapping.path()).noneMatch(path -> path.startsWith("/api/solutions"));
                }
            }
        }
    }
}
