package vn.tsolve.ticket.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.testcontainers.context.ImportTestcontainers;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import vn.tsolve.common.audit.AuditService;
import vn.tsolve.common.security.Role;
import vn.tsolve.common.security.TSolveUser;
import vn.tsolve.ticket.TicketTestDatabase;

/** Manual entry over HTTP against a real PostgreSQL: status codes, ProblemDetail bodies and what is stored. */
@SpringBootTest
@AutoConfigureMockMvc
@ImportTestcontainers(TicketTestDatabase.class)
@TestPropertySource(
        properties = {
            "tsolve.stub.projects=11111111-1111-1111-1111-111111111111:00000000-0000-0000-0000-000000000001",
            "tsolve.cors.allowed-origins=http://localhost:5173",
            "server.port=0",
            // the sign-in values the application needs to start (identity)
            "TSOLVE_LOGIN_ORIGIN=http://login.localhost:8081",
            "TSOLVE_ADMIN_ORIGIN=http://admin.localhost:8081",
            "TSOLVE_SIGNING_KEY=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
            "GOOGLE_CLIENT_ID=test-client-id",
            "GOOGLE_CLIENT_SECRET=test-secret",
            "PLATFORM_OPERATOR_EMAILS=ops@example.test",
            "DB_HOST=unused",
            "DB_PORT=0",
            "DB_NAME=unused",
            "DB_USER=unused",
            "DB_PASSWORD=unused"
        })
class ManualTicketControllerIT {

    static final String PROJECT = "00000000-0000-0000-0000-000000000001";
    static final String OTHER_PROJECT = "00000000-0000-0000-0000-000000000002";
    static final TSolveUser PM = new TSolveUser(
            UUID.fromString("11111111-1111-1111-1111-111111111111"),
            "manual-pm@example.test",
            "Nguyễn Văn An",
            Set.of(Role.PROJECT_MANAGER));
    static final TSolveUser EMPLOYEE = new TSolveUser(
            UUID.fromString("33333333-3333-3333-3333-333333333333"), "emp@example.test", "Employee", Set.of());

    @MockBean
    private AuditService audit;

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void cleanDatabase() {
        jdbc.execute("delete from ticket_import_decision");
        jdbc.execute("delete from ticket");
        jdbc.execute("delete from import_batch");
        jdbc.execute("delete from import_preview");
        // the writer_id of a manual ticket references the caller's account
        jdbc.update(
                "insert into app_user (id, email, full_name, status, created_at) values (?, ?, ?, 'ACTIVE', now())"
                        + " on conflict do nothing",
                PM.id(),
                PM.email(),
                PM.fullName());
    }

    static String body(String projectId, String ticketId, String title) {
        return """
                {"projectId": %s, "ticketId": %s, "title": %s, "description": "Users get 500",
                 "resolvedDate": "2026-01-15", "symptoms": "Error 500", "rootCause": "Bad token",
                 "resolutionSteps": "Restart", "workaround": null, "scope": "All users"}
                """.formatted(quoted(projectId), quoted(ticketId), quoted(title));
    }

    static String quoted(String value) {
        return value == null ? "null" : "\"" + value + "\"";
    }

    ResultActions postAs(TSolveUser user, String url, String json) throws Exception {
        return mvc.perform(post(url)
                .with(authentication(new UsernamePasswordAuthenticationToken(user, null, user.authorities())))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json));
    }

    int count(String table) {
        return jdbc.queryForObject("select count(*) from " + table, Integer.class);
    }

    @Test
    void AC_006_1_savesAPublishedManualTicketWithItsBatchAndDecision() throws Exception {
        postAs(PM, "/api/v1/imports/manual", body(PROJECT, " ITSUP-9 ", "Login fails"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.created").value(1))
                .andExpect(jsonPath("$.skipped").value(0))
                .andExpect(jsonPath("$.message.code").value("MSG32"))
                .andExpect(jsonPath("$.message.params.created").value(1))
                .andExpect(jsonPath("$.message.params.skipped").value(0));

        Map<String, Object> ticket = jdbc.queryForMap(
                "select source_ticket_id, source, status, writer, writer_id, resolved_date::text as resolved,"
                        + " import_batch_id from ticket");
        assertThat(ticket)
                .containsEntry("source_ticket_id", "ITSUP-9")
                .containsEntry("source", "MANUAL")
                .containsEntry("status", "PUBLISHED")
                .containsEntry("writer", "Nguyễn Văn An")
                .containsEntry("writer_id", PM.id())
                .containsEntry("resolved", "2026-01-15");
        assertThat(jdbc.queryForMap("select id, source, status, file_name, published_count, error_count, removed_count,"
                        + " performed_by from import_batch"))
                .containsEntry("id", ticket.get("import_batch_id"))
                .containsEntry("source", "MANUAL")
                .containsEntry("status", "COMPLETED")
                .containsEntry("file_name", null)
                .containsEntry("published_count", 1)
                .containsEntry("error_count", 0)
                .containsEntry("removed_count", 0)
                .containsEntry("performed_by", PM.getName());
        assertThat(jdbc.queryForMap("select reviewer, decision from ticket_import_decision"))
                .containsEntry("reviewer", PM.getName())
                .containsEntry("decision", "APPROVE");
    }

    @Test
    void AC_006_3_AC_006_6_listsEveryInvalidFieldIn400AndSavesNothing() throws Exception {
        postAs(PM, "/api/v1/imports/manual", body(null, "  ", "t".repeat(201)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MSG01"))
                .andExpect(jsonPath("$.fieldErrors.length()").value(3))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("projectId"))
                .andExpect(jsonPath("$.fieldErrors[0].code").value("MSG01"))
                .andExpect(jsonPath("$.fieldErrors[1].field").value("ticketId"))
                .andExpect(jsonPath("$.fieldErrors[1].code").value("MSG01"))
                .andExpect(jsonPath("$.fieldErrors[2].field").value("title"))
                .andExpect(jsonPath("$.fieldErrors[2].code").value("MSG02"))
                .andExpect(jsonPath("$.fieldErrors[2].params.max_length").value(200));

        assertThat(count("ticket")).isZero();
        assertThat(count("import_batch")).isZero();
    }

    @Test
    void AC_006_7_answers409Msg31ForATakenTicketIdAndRecordsNoBatch() throws Exception {
        postAs(PM, "/api/v1/imports/manual", body(PROJECT, "ITSUP-9", "First")).andExpect(status().isCreated());

        postAs(PM, "/api/v1/imports/manual", body(PROJECT, " ITSUP-9 ", "Second"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("MSG31"))
                .andExpect(jsonPath("$.params.ticket_id").value("ITSUP-9"));

        assertThat(count("ticket")).isEqualTo(1);
        assertThat(count("import_batch")).isEqualTo(1);
    }

    @Test
    void AC_006_8_answers403Msg08ForAnUnmanagedProjectOrNoProjectManagerRole() throws Exception {
        postAs(PM, "/api/v1/imports/manual", body(OTHER_PROJECT, "ITSUP-9", "Login fails"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("MSG08"));
        postAs(EMPLOYEE, "/api/v1/imports/manual", body(PROJECT, "ITSUP-9", "Login fails"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("MSG08"));

        assertThat(count("ticket")).isZero();
        assertThat(count("import_batch")).isZero();
    }

    static String withDraft(String json, long draftId) {
        return json.substring(0, json.lastIndexOf('}')) + ", \"draftId\": " + draftId + "}";
    }

    ResultActions as(TSolveUser user, MockHttpServletRequestBuilder request) throws Exception {
        return mvc.perform(
                request.with(authentication(new UsernamePasswordAuthenticationToken(user, null, user.authorities())))
                        .with(csrf()));
    }

    long saveDraft(String json) throws Exception {
        String answer = postAs(PM, "/api/v1/imports/manual/drafts", json)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message.code").value("MSG41"))
                .andReturn()
                .getResponse()
                .getContentAsString();
        return ((Number) JsonPath.read(answer, "$.previewId")).longValue();
    }

    @Test
    void AC_006_9_to_AC_006_12_savesReopensUpdatesAndFinishesAManualDraft() throws Exception {
        // a Draft is not validated: blank Ticket ID, title over 200 characters, an unreadable date
        long draft = saveDraft("""
                {"projectId": "%s", "ticketId": " ", "title": "%s", "resolvedDate": "soon"}
                """.formatted(PROJECT, "t".repeat(201)));

        as(PM, get("/api/v1/imports/drafts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].previewId").value(draft))
                .andExpect(jsonPath("$[0].source").value("MANUAL"))
                .andExpect(jsonPath("$[0].rowCount").value(1));
        as(PM, get("/api/v1/imports/manual/drafts/" + draft))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.projectId").value(PROJECT))
                .andExpect(jsonPath("$.ticketId").value(" "))
                .andExpect(jsonPath("$.title").value("t".repeat(201)))
                .andExpect(jsonPath("$.resolvedDate").value("soon"))
                .andExpect(jsonPath("$.savedAt").exists());
        // the preview endpoints do not serve a Manual Draft (AC-006.13)
        as(PM, get("/api/v1/imports/previews/" + draft)).andExpect(status().isNotFound());

        assertThat(saveDraft(withDraft(body(PROJECT, "ITSUP-9", "Login fails"), draft)))
                .isEqualTo(draft);
        assertThat(count("import_preview")).isEqualTo(1);
        as(PM, get("/api/v1/imports/manual/drafts/" + draft))
                .andExpect(jsonPath("$.ticketId").value("ITSUP-9"))
                .andExpect(jsonPath("$.title").value("Login fails"))
                .andExpect(jsonPath("$.resolvedDate").value("2026-01-15"));

        // a rejected save keeps the Draft (AC-006.12)
        postAs(PM, "/api/v1/imports/manual", withDraft(body(PROJECT, "ITSUP-9", " "), draft))
                .andExpect(status().isBadRequest());
        assertThat(count("import_preview")).isEqualTo(1);

        postAs(PM, "/api/v1/imports/manual", withDraft(body(PROJECT, "ITSUP-9", "Login fails"), draft))
                .andExpect(status().isCreated());
        assertThat(count("ticket")).isEqualTo(1);
        assertThat(count("import_preview")).isZero();
        assertThat(count("import_preview_row")).isZero();
        as(PM, get("/api/v1/imports/manual/drafts/" + draft))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("MSG04"));
    }

    @Test
    void AC_006_13_deletesAManualDraftAndAnswers404ToAnotherUser() throws Exception {
        long draft = saveDraft("{\"projectId\": \"" + PROJECT + "\"}");
        TSolveUser otherPm = new TSolveUser(
                UUID.fromString("44444444-4444-4444-4444-444444444444"),
                "other-pm@example.test",
                "Other PM",
                Set.of(Role.PROJECT_MANAGER));

        as(otherPm, get("/api/v1/imports/manual/drafts/" + draft))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("MSG04"));
        as(otherPm, delete("/api/v1/imports/drafts/" + draft)).andExpect(status().isNotFound());

        as(PM, delete("/api/v1/imports/drafts/" + draft)).andExpect(status().isNoContent());
        assertThat(count("import_preview")).isZero();
        assertThat(count("import_preview_row")).isZero();
    }
}
