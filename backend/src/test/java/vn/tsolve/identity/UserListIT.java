package vn.tsolve.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import vn.tsolve.common.security.Role;
import vn.tsolve.common.security.WithTSolveUser;
import vn.tsolve.identity.signin.port.TenantScope;

@SpringBootTest(
        properties = {
            "TSOLVE_LOGIN_ORIGIN=http://127.0.0.1:9",
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
            "DB_PASSWORD=unused"
        })
@AutoConfigureMockMvc
@Import(TwoTenantDatabases.class)
@WithTSolveUser(roles = Role.ADMIN)
class UserListIT {
    private static final String DOMAIN = "@list-fixture.test";
    private static final UUID DEPARTMENT = new UUID(107, 1);
    private static final UUID SECOND_DEPARTMENT = new UUID(107, 2);
    private static final UUID INACTIVE_DEPARTMENT = new UUID(107, 3);
    private static final Instant EARLY = Instant.parse("2026-01-01T00:00:00Z");
    private static final Instant LATE = Instant.parse("2026-02-01T00:00:00Z");
    private static final List<Row> ROWS = List.of(
            new Row(new UUID(107, 11), "Alpha%_\\", "a" + DOMAIN, EARLY),
            new Row(new UUID(107, 12), "Same", "c" + DOMAIN, LATE),
            new Row(new UUID(107, 13), "same", "b" + DOMAIN, LATE),
            new Row(new UUID(107, 14), null, "d" + DOMAIN, null),
            new Row(new UUID(107, 15), "", "e" + DOMAIN, null),
            new Row(new UUID(107, 16), "Zulu", "f" + DOMAIN, EARLY));

    @Autowired
    private MockMvc mvc;

    @Autowired
    private TenantScope scope;

    @Autowired
    private ObjectMapper mapper;

    private record Row(UUID id, String name, String email, Instant signedIn) {}

    @BeforeEach
    void seed() {
        for (JdbcTemplate db : List.of(TwoTenantDatabases.tenantA(), TwoTenantDatabases.tenantB())) {
            db.update("DELETE FROM app_user WHERE email LIKE ?", "%" + DOMAIN);
            db.update(
                    "DELETE FROM department WHERE public_id IN (?, ?, ?)",
                    DEPARTMENT,
                    SECOND_DEPARTMENT,
                    INACTIVE_DEPARTMENT);
        }
        JdbcTemplate db = TwoTenantDatabases.tenantA();
        db.update(
                "INSERT INTO department(public_id, name, status) VALUES (?, 'List fixture Alpha', 'ACTIVE'), (?, 'list fixture beta', 'ACTIVE'), (?, 'List fixture hidden', 'DEACTIVATED')",
                DEPARTMENT,
                SECOND_DEPARTMENT,
                INACTIVE_DEPARTMENT);
        for (Row row : ROWS) {
            db.update(
                    "INSERT INTO app_user(id, email, full_name, status, created_at, last_sign_in_at) VALUES (?, ?, ?, ?, now(), ?)",
                    row.id(),
                    row.email(),
                    row.name(),
                    row == ROWS.get(5) ? "DEACTIVATED" : "ACTIVE",
                    row.signedIn() == null ? null : java.sql.Timestamp.from(row.signedIn()));
        }
        db.update(
                "UPDATE app_user SET department_id = (SELECT department_id FROM department WHERE public_id = ?) WHERE id = ?",
                DEPARTMENT,
                ROWS.getFirst().id());
        db.update(
                "INSERT INTO user_role(user_id, role, assigned_at) VALUES (?, 'ADMIN', now()), (?, 'TICKET_RESOLVER', now()), (?, 'PROJECT_MANAGER', now()), (?, 'KNOWLEDGE_MANAGER', now())",
                ROWS.getFirst().id(),
                ROWS.getFirst().id(),
                ROWS.get(1).id(),
                ROWS.get(2).id());
        TwoTenantDatabases.tenantB()
                .update(
                        "INSERT INTO app_user(email, full_name, status, created_at) VALUES (?, 'Beta secret', 'ACTIVE', now())",
                        "beta" + DOMAIN);
    }

    private MockHttpServletRequestBuilder users() {
        return get("/api/v1/users").param("q", DOMAIN);
    }

    private ResultActions perform(MockHttpServletRequestBuilder request) {
        return scope.runIn(TwoTenantDatabases.ACME, () -> {
            try {
                return mvc.perform(request);
            } catch (Exception e) {
                throw new AssertionError(e);
            }
        });
    }

    private JsonNode body(MockHttpServletRequestBuilder request) throws Exception {
        return mapper.readTree(perform(request)
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());
    }

    private List<String> ids(JsonNode body) {
        List<String> result = new ArrayList<>();
        body.get("content").forEach(row -> result.add(row.get("id").asText()));
        return result;
    }

    @Test
    void exposesContractFieldsRolesDepartmentAndExplicitNulls_AC_004_9() throws Exception {
        JsonNode admin = body(users().param("role", "ADMIN")).get("content").get(0);
        assertThat(admin.get("id").asText()).isEqualTo(ROWS.getFirst().id().toString());
        assertThat(admin.get("fullName").asText()).isEqualTo("Alpha%_\\");
        assertThat(admin.get("email").asText()).isEqualTo("a" + DOMAIN);
        assertThat(admin.get("roles").toString()).isEqualTo("[\"EMPLOYEE\",\"TICKET_RESOLVER\",\"ADMIN\"]");
        assertThat(admin.at("/department/departmentId").asText()).isEqualTo(DEPARTMENT.toString());
        assertThat(admin.at("/department/name").asText()).isEqualTo("List fixture Alpha");
        assertThat(admin.get("status").asText()).isEqualTo("ACTIVE");
        assertThat(Instant.parse(admin.get("lastSignInAt").asText())).isEqualTo(EARLY);
        for (JsonNode row : body(users()).get("content")) {
            if (List.of(ROWS.get(3).id().toString(), ROWS.get(4).id().toString())
                    .contains(row.get("id").asText())) {
                assertThat(row.has("fullName") && row.get("fullName").isNull()).isTrue();
                assertThat(row.has("department") && row.get("department").isNull())
                        .isTrue();
                assertThat(row.has("lastSignInAt") && row.get("lastSignInAt").isNull())
                        .isTrue();
            }
        }
    }

    @ParameterizedTest
    @CsvSource({"ADMIN,1", "TICKET_RESOLVER,1", "PROJECT_MANAGER,1", "KNOWLEDGE_MANAGER,1", "EMPLOYEE,3"})
    void filtersRoleMembershipAndEmployeeOnly_AC_004_10(String role, int count) throws Exception {
        assertThat(body(users().param("role", role)).at("/page/totalElements").asInt())
                .isEqualTo(count);
    }

    @Test
    void combinesFiltersAndSearchesLiteralWildcards_AC_004_10() throws Exception {
        assertThat(body(users().param("status", "DEACTIVATED"))
                        .at("/page/totalElements")
                        .asInt())
                .isEqualTo(1);
        assertThat(body(users().param("departmentId", DEPARTMENT.toString())
                                .param("role", "ADMIN")
                                .param("status", "ACTIVE"))
                        .at("/page/totalElements")
                        .asInt())
                .isEqualTo(1);
        assertThat(body(users().param("departmentId", DEPARTMENT.toString()).param("status", "DEACTIVATED"))
                        .at("/page/totalElements")
                        .asInt())
                .isZero();
        for (String q : List.of("%", "_", "\\", "  ALPHA%_\\  ", "a" + DOMAIN)) {
            assertThat(ids(body(get("/api/v1/users").param("q", q))))
                    .containsExactly(ROWS.getFirst().id().toString());
        }
        assertThat(ids(body(get("/api/v1/users").param("q", "  SAME  "))))
                .containsExactly(ROWS.get(1).id().toString(), ROWS.get(2).id().toString());
    }

    @ParameterizedTest
    @CsvSource({"fullName,asc", "fullName,desc", "email,asc", "email,desc", "lastSignInAt,asc", "lastSignInAt,desc"})
    void sortsBothDirectionsWithNullPlacementAndIdTieBreak_AC_004_11(String field, String direction) throws Exception {
        Comparator<Row> order =
                switch (field) {
                    case "email" -> Comparator.comparing(row -> row.email().toLowerCase(Locale.ROOT));
                    case "lastSignInAt" -> Comparator.comparing(
                            Row::signedIn, Comparator.nullsFirst(Comparator.naturalOrder()));
                    default -> Comparator.comparing(
                            row -> row.name() == null ? "" : row.name().toLowerCase(Locale.ROOT));
                };
        if (direction.equals("desc")) {
            order = order.reversed();
        }
        List<String> expected = ROWS.stream()
                .sorted(order.thenComparing(Row::id))
                .map(row -> row.id().toString())
                .toList();
        assertThat(ids(body(users().param("sort", field + "," + direction)))).isEqualTo(expected);
    }

    @Test
    void unknownSortFallsBackAndRepeatedSortUsesFirstValid_AC_004_11() throws Exception {
        assertThat(ids(body(users().param("sort", "id,desc")))).isEqualTo(ids(body(users())));
        assertThat(ids(body(users().param("sort", "invalid,asc", "email,desc", "fullName,asc"))))
                .isEqualTo(ids(body(users().param("sort", "email,desc"))));
    }

    @Test
    void reportsFilteredTotalsAndEmptyPastEnd_AC_004_12() throws Exception {
        JsonNode page = body(users().param("size", "2").param("page", "1"));
        assertThat(page.get("content").size()).isEqualTo(2);
        assertThat(page.at("/page/size").asInt()).isEqualTo(2);
        assertThat(page.at("/page/number").asInt()).isEqualTo(1);
        assertThat(page.at("/page/totalElements").asInt()).isEqualTo(6);
        assertThat(page.at("/page/totalPages").asInt()).isEqualTo(3);
        JsonNode past = body(users().param("size", "2").param("page", "99"));
        assertThat(past.get("content").isEmpty()).isTrue();
        assertThat(past.at("/page/totalElements").asInt()).isEqualTo(6);
        assertThat(past.at("/page/totalPages").asInt()).isEqualTo(3);
        assertThat(body(users().param("size", "500")).at("/page/size").asInt()).isEqualTo(100);
    }

    @Test
    void listsActiveDepartmentsIgnoringCaseThenId_AC_004_14() throws Exception {
        JsonNode departments = body(get("/api/v1/users/departments"));
        List<String> fixtureIds = new ArrayList<>();
        departments.forEach(row -> {
            if (row.get("name").asText().toLowerCase(Locale.ROOT).startsWith("list fixture")) {
                fixtureIds.add(row.get("departmentId").asText());
            }
        });
        assertThat(fixtureIds).containsExactly(DEPARTMENT.toString(), SECOND_DEPARTMENT.toString());
    }

    @Test
    void neverReturnsOrCountsBetaAndFailsUnbound_AC_004_16() throws Exception {
        assertThat(body(users()).at("/page/totalElements").asInt()).isEqualTo(6);
        assertThat(body(users()).toString()).doesNotContain("Beta secret", "beta" + DOMAIN);
        mvc.perform(users())
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("MSG06"));
    }

    @Test
    @WithAnonymousUser
    void doesNotSignInWhenProfileInactive_AC_004_17() throws Exception {
        perform(users())
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("MSG07"));
    }
}
