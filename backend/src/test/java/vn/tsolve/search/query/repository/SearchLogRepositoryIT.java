package vn.tsolve.search.query.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import vn.tsolve.search.embedding.service.impl.EmbeddingCatchUpJob;
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
            "SEARCH_DEV_SCOPE="
        })
class SearchLogRepositoryIT {
    private static final SharedPostgres.Db DB = SharedPostgres.migratedDatabase();

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        DB.register(registry);
    }

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private SearchLogRepository logs;

    @MockBean
    private EmbeddingCatchUpJob catchUpJob;

    @Test
    void writesRowWithDepartmentOfFilter_AC_007_6() {
        UUID publicId = UUID.randomUUID();
        Long department = jdbc.queryForObject(
                "INSERT INTO department(name,status,public_id) VALUES ('IT','ACTIVE',?) RETURNING department_id",
                Long.class,
                publicId);
        UUID user = jdbc.queryForObject(
                "INSERT INTO app_user(email,status,created_at) VALUES (?,'ACTIVE',now()) RETURNING id",
                UUID.class,
                UUID.randomUUID() + "@example.test");

        logs.log(user, publicId.toString(), "VPN", "WEB", 43, true);
        var row = jdbc.queryForMap("SELECT * FROM search_log WHERE user_id = ?", user);
        assertThat(row)
                .containsEntry("user_id", user)
                .containsEntry("department_id", department)
                .containsEntry("query", "VPN")
                .containsEntry("channel", "WEB")
                .containsEntry("result_count", 43)
                .containsEntry("is_fallback", true);
        assertThat(row.get("search_log_id")).isInstanceOf(UUID.class);
        assertThat(row.get("created_at")).isNotNull();
        assertThat(logs.findById((UUID) row.get("search_log_id"))).isPresent();

        logs.log(user, null, "no filter", "WEB", 0, false);
        logs.log(user, UUID.randomUUID().toString(), "unknown filter", "WEB", 0, false);
        assertThat(jdbc.queryForList(
                        "SELECT department_id FROM search_log WHERE user_id = ? AND result_count = 0", user))
                .hasSize(2)
                .allSatisfy(result -> assertThat(result.get("department_id")).isNull());
    }
}
