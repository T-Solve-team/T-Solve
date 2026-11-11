package vn.tsolve.search.query.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import vn.tsolve.ai.client.EmbeddingClient;
import vn.tsolve.ai.exception.AiUnavailableException;
import vn.tsolve.search.embedding.repository.PgVector;
import vn.tsolve.search.embedding.service.impl.EmbeddingCatchUpJob;
import vn.tsolve.search.query.dto.RankFilter;
import vn.tsolve.search.query.dto.TicketSearchResultResponse;
import vn.tsolve.search.query.service.TicketRankingService;
import vn.tsolve.search.scope.service.VisibleScope.Scope;
import vn.tsolve.testsupport.SharedPostgres;

@SpringBootTest
@TestPropertySource(
        properties = {
            "TSOLVE_LOGIN_ORIGIN=http://login.localhost:8081",
            "TSOLVE_ADMIN_ORIGIN=http://admin.localhost:8081",
            "TSOLVE_SIGNING_KEY=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
            "GOOGLE_CLIENT_ID=test-client-id",
            "GOOGLE_CLIENT_SECRET=test-secret",
            "PLATFORM_OPERATOR_EMAILS=ops@example.test",
            "CORS_ALLOWED_ORIGINS=http://localhost:5173",
            "DB_HOST=unused",
            "DB_PORT=0",
            "DB_NAME=unused",
            "DB_USER=unused",
            "DB_PASSWORD=unused",
            "OLLAMA_BASE_URL=http://127.0.0.1:11434",
            "OLLAMA_CHAT_MODEL=test",
            "SEARCH_DEV_SCOPE=",
            "SEARCH_MIN_SIMILARITY=0.5"
        })
class TicketRankingRepositoryIT {
    private static final SharedPostgres.Db DB = SharedPostgres.migratedDatabase();

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        DB.register(registry);
    }

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private TicketRankingService ranking;

    @MockBean
    private EmbeddingClient embeddings;

    @MockBean
    private EmbeddingCatchUpJob catchUpJob;

    private final UUID departmentPublicId = UUID.randomUUID();
    private final UUID otherPublicId = UUID.randomUUID();
    private long department;
    private long deletedDepartment;
    private UUID project;
    private long ticketSequence;
    private UUID best;
    private UUID tied;
    private UUID weaker;
    private UUID undated;

    @BeforeEach
    void seed() {
        jdbc.execute("TRUNCATE ticket_import_decision, ticket, project, department CASCADE");
        ticketSequence = 0;
        department = department("IT", "ACTIVE", departmentPublicId);
        project = project(department, "ITSUP");
        best = ticket(project, "BEST", "PUBLISHED", "2026-10-01", "VPN 50%_off\\", "CSV");
        tied = ticket(project, "TIED", "PUBLISHED", "2026-10-01", "VPN 50%_off\\", "CSV");
        weaker = ticket(project, "WEAKER", "PUBLISHED", "2026-10-02", "VPN", "MANUAL");
        undated = ticket(project, "UNDATED", "PUBLISHED", null, "VPN", "CSV");
        vector(best, 1, 0);
        vector(tied, 1, 0);
        vector(weaker, 0.8f, 0.6f);
        vector(undated, 0.6f, 0.8f);
        vector(ticket(project, "LOW", "PUBLISHED", "2026-10-03", "VPN 50ABoff\\", "CSV"), 0, 1);
        ticket(project, "NO-VECTOR", "PUBLISHED", "2026-10-04", "VPN", "CSV");
        vector(ticket(project, "HIDDEN", "UNPUBLISHED", "2026-10-05", "VPN", "CSV"), 1, 0);
        deletedDepartment = department("OLD", "DELETED", UUID.randomUUID());
        vector(ticket(project(deletedDepartment, "OLD"), "DELETED", "PUBLISHED", "2026-10-05", "VPN", "CSV"), 1, 0);
        vector(
                ticket(
                        project(department("HR", "ACTIVE", otherPublicId), "HR"),
                        "OUTSIDE",
                        "PUBLISHED",
                        "2026-10-05",
                        "VPN",
                        "CSV"),
                1,
                0);
        when(embeddings.embed(any())).thenAnswer(call -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive())
                    .isFalse();
            return List.of(queryVector());
        });
    }

    @Test
    void searchJoinsProjectByPublicId_BUG_153() {
        assertProjectJoined();
    }

    @Test
    void searchJoinsLegacyProjectUuid_BUG_153() {
        Long projectId = jdbc.queryForObject("SELECT project_id FROM project WHERE public_id = ?", Long.class, project);
        jdbc.update("UPDATE ticket SET project_id = ? WHERE project_id = ?", new UUID(0, projectId), project);
        assertProjectJoined();
    }

    private void assertProjectJoined() {
        var semantic = rank(new RankFilter(null, null, null, null), PageRequest.of(0, 2));
        assertThat(semantic.getTotalElements()).isEqualTo(4);
        assertThat(semantic.getContent())
                .extracting(row -> row.project().projectId())
                .containsOnly(project);
        when(embeddings.embed(any())).thenThrow(new AiUnavailableException());
        var keyword = rank(new RankFilter(null, null, null, null), PageRequest.of(0, 2));
        assertThat(keyword.getTotalElements()).isEqualTo(6);
        assertThat(keyword.getContent())
                .extracting(row -> row.project().projectId())
                .containsOnly(project);
    }

    @Test
    void ranksBySimilarityAboveCutOff_AC_007_1() {
        var first = rank(new RankFilter(null, null, null, null), PageRequest.of(0, 2));
        var second = rank(new RankFilter(null, null, null, null), PageRequest.of(1, 2));
        assertThat(first.getTotalElements()).isEqualTo(4);
        assertThat(first.stream().map(TicketSearchResultResponse::title)).containsExactly("BEST", "TIED");
        assertThat(second.stream().map(TicketSearchResultResponse::title)).containsExactly("WEAKER", "UNDATED");
        float[] opposite = queryVector();
        opposite[0] = -1;
        when(embeddings.embed(any())).thenReturn(List.of(opposite));
        assertThat(rank(new RankFilter(null, null, null, null), PageRequest.of(0, 20)))
                .isEmpty();
    }

    @Test
    void neverReturnsUnpublishedOrDeletedDepartment_AC_007_1() {
        assertThat(rank(new RankFilter(null, null, null, null), PageRequest.of(0, 20)).stream()
                        .map(TicketSearchResultResponse::title))
                .containsExactly("BEST", "TIED", "WEAKER", "UNDATED");
    }

    @Test
    void matchesKeywordLiterallyNewestFirst_AC_007_2() {
        when(embeddings.embed(any())).thenThrow(new AiUnavailableException());
        var literal = ranking.rank(
                "unused", "50%_off\\", new RankFilter(null, null, null, null), scope(), PageRequest.of(0, 1));
        assertThat(literal.isFallback()).isTrue();
        assertThat(literal.page().getTotalElements()).isEqualTo(2);
        assertThat(literal.page().getContent().getFirst().title()).isEqualTo("TIED");
        var result = rank(new RankFilter(null, null, null, null), PageRequest.of(0, 20));
        assertThat(result.stream().map(TicketSearchResultResponse::title))
                .containsExactly("NO-VECTOR", "LOW", "WEAKER", "TIED", "BEST", "UNDATED");
    }

    @Test
    void appliesFiltersAndInclusiveDates_AC_007_3() {
        RankFilter filter =
                new RankFilter(departmentPublicId, "CSV", LocalDate.parse("2026-10-01"), LocalDate.parse("2026-10-01"));
        assertThat(rank(filter, PageRequest.of(0, 20)).stream().map(TicketSearchResultResponse::title))
                .containsExactly("BEST", "TIED");
        when(embeddings.embed(any())).thenThrow(new AiUnavailableException());
        assertThat(rank(filter, PageRequest.of(0, 20)).stream().map(TicketSearchResultResponse::title))
                .containsExactly("TIED", "BEST");
    }

    @Test
    void returnsEmptyForDepartmentOutsideScope_AC_007_3() {
        for (UUID id : List.of(otherPublicId, UUID.randomUUID())) {
            assertThat(rank(new RankFilter(id, null, null, null), PageRequest.of(0, 20)))
                    .isEmpty();
        }
        when(embeddings.embed(any())).thenThrow(new AiUnavailableException());
        assertThat(rank(new RankFilter(otherPublicId, null, null, null), PageRequest.of(0, 20)))
                .isEmpty();
    }

    @Test
    void answersWithNoFilterAtAll_AC_007_3() {
        assertThat(rank(new RankFilter(null, null, null, null), PageRequest.of(0, 20))
                        .getTotalElements())
                .isEqualTo(4);
        when(embeddings.embed(any())).thenThrow(new AiUnavailableException());
        assertThat(rank(new RankFilter(null, null, null, null), PageRequest.of(0, 20))
                        .getTotalElements())
                .isEqualTo(6);
    }

    @Test
    void returnsRoundedHelpfulRateOrNull_AC_007_4() {
        feedback(best, "HELPFUL");
        feedback(best, "HELPFUL");
        feedback(best, "NOT_HELPFUL");
        feedback(tied, "HELPFUL");
        feedback(tied, "NOT_HELPFUL");
        var rows = rank(new RankFilter(null, null, null, null), PageRequest.of(0, 20))
                .getContent();
        assertThat(rows).extracting(TicketSearchResultResponse::helpfulRate).containsExactly(67, 50, null, null);
        assertThat(rows.getFirst().excerpt()).isEqualTo("VPN 50%_off\\ Restart router");
    }

    @Test
    void returnsResolvedAtAsStartOfVietnamDayOrNull_AC_007_5() {
        var rows = rank(new RankFilter(null, null, null, null), PageRequest.of(0, 20))
                .getContent();
        assertThat(rows.getFirst().resolvedAt()).isEqualTo(OffsetDateTime.parse("2026-10-01T00:00:00+07:00"));
        assertThat(rows.getLast().resolvedAt()).isNull();
    }

    @Test
    void usesJiraProjectKeyAsProjectName_AC_006_6() {
        var row = rank(new RankFilter(null, null, null, null), PageRequest.of(0, 20))
                .getContent()
                .getFirst();
        assertThat(row.project().name()).isEqualTo("ITSUP");
        assertThat(row.project().projectId()).isEqualTo(project);
        assertThat(row.department().departmentId()).isEqualTo(departmentPublicId);
        assertThat(row.department().name()).isEqualTo("IT");
        assertThat(row.ticketId())
                .isEqualTo(jdbc.queryForObject("SELECT public_id FROM ticket WHERE ticket_id = ?", UUID.class, best));
    }

    private Page<TicketSearchResultResponse> rank(RankFilter filter, PageRequest page) {
        return ranking.rank("VPN", "VPN", filter, scope(), page).page();
    }

    private Scope scope() {
        return new Scope(Set.of(department, deletedDepartment), Set.of(), Optional.empty());
    }

    private static float[] queryVector() {
        float[] vector = new float[EmbeddingClient.DIMENSIONS];
        vector[0] = 1;
        return vector;
    }

    private void vector(UUID ticket, float first, float second) {
        float[] vector = queryVector();
        vector[0] = first;
        vector[1] = second;
        jdbc.update(
                "INSERT INTO ticket_embedding(ticket_id,embedding,model) VALUES (?,CAST(? AS vector),'bge-m3')",
                ticket,
                PgVector.literal(vector));
    }

    private long department(String name, String status, UUID publicId) {
        return jdbc.queryForObject(
                "INSERT INTO department(name,status,public_id) VALUES (?,?,?) RETURNING department_id",
                Long.class,
                name,
                status,
                publicId);
    }

    private UUID project(long departmentId, String key) {
        return jdbc.queryForObject(
                "INSERT INTO project(department_id,jira_project_key) VALUES (?,?) RETURNING public_id",
                UUID.class,
                departmentId,
                key);
    }

    private UUID ticket(UUID projectId, String title, String status, String date, String symptoms, String source) {
        return jdbc.queryForObject(
                """
                INSERT INTO ticket(ticket_id, source_ticket_id, project_id, source, status, title, writer,
                    symptoms, root_cause, resolution_steps, scope, resolved_date)
                VALUES (?,?,?,?,?,?,'writer',?,'Root cause',E'  Restart\\nrouter  ','Scope',CAST(? AS date)) RETURNING ticket_id
                """,
                UUID.class,
                new UUID(0, ++ticketSequence),
                title,
                projectId,
                source,
                status,
                title,
                symptoms,
                date);
    }

    private void feedback(UUID ticket, String rating) {
        UUID user = jdbc.queryForObject(
                "INSERT INTO app_user(email,status,created_at) VALUES (?,'ACTIVE',now()) RETURNING id",
                UUID.class,
                UUID.randomUUID() + "@example.test");
        jdbc.update(
                "INSERT INTO ticket_feedback(ticket_id,user_id,rating,channel) VALUES (?,?,?,'WEB')",
                ticket,
                user,
                rating);
    }
}
