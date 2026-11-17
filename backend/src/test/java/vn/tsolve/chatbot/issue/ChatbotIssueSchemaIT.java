package vn.tsolve.chatbot.issue;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import javax.sql.DataSource;
import liquibase.Liquibase;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.ClassLoaderResourceAccessor;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import vn.tsolve.identity.TwoTenantDatabases;

class ChatbotIssueSchemaIT {
    private static final String CHANGELOG = "db/changelog/db.changelog-master.xml";
    private static DataSource source;
    private static JdbcTemplate jdbc;

    @BeforeAll
    static void migrateDatabaseOnSharedServer() throws Exception {
        source = TwoTenantDatabases.emptyDatabase("chatbot_issue_schema");
        jdbc = new JdbcTemplate(source);
        try (var liquibase = liquibase()) {
            liquibase.update("");
        }
    }

    @Test
    void addsNullableVarchar255IssueKey_AC_011_11() {
        assertColumn();
    }

    @Test
    void declaresAndExecutesRollbackOfOnlyTheIssueKey_AC_011_11() throws Exception {
        assertColumn();
        var sql = new ClassPathResource("db/changelog/changes/044-chatbot-answer-jira-issue.sql")
                .getContentAsString(StandardCharsets.UTF_8);
        assertThat(sql)
                .contains(
                        "--changeset chatbot:044-chatbot-answer-jira-issue",
                        "--rollback ALTER TABLE chatbot_answer DROP COLUMN jira_issue_key;");
        String rollback = sql.lines()
                .filter(line -> line.startsWith("--rollback "))
                .map(line -> line.substring("--rollback ".length()))
                .findFirst()
                .orElseThrow();
        try {
            jdbc.execute(rollback);
            assertThat(jdbc.queryForObject("SELECT to_regclass('public.chatbot_answer')::text", String.class))
                    .isEqualTo("chatbot_answer");
            assertThat(jdbc.queryForList("""
                    SELECT column_name FROM information_schema.columns
                    WHERE table_schema = 'public' AND table_name = 'chatbot_answer'
                    """, String.class))
                    .contains("rating", "rated_at", "question", "answer_text")
                    .doesNotContain("jira_issue_key");
        } finally {
            jdbc.execute("ALTER TABLE chatbot_answer ADD COLUMN jira_issue_key VARCHAR(255) NULL");
        }
        assertColumn();
    }

    private static void assertColumn() {
        assertThat(jdbc.queryForMap("""
                SELECT data_type, is_nullable, character_maximum_length
                FROM information_schema.columns
                WHERE table_schema = 'public' AND table_name = 'chatbot_answer' AND column_name = 'jira_issue_key'
                """))
                .containsEntry("data_type", "character varying")
                .containsEntry("is_nullable", "YES")
                .containsEntry("character_maximum_length", 255);
    }

    private static Liquibase liquibase() throws Exception {
        return new Liquibase(CHANGELOG, new ClassLoaderResourceAccessor(), new JdbcConnection(source.getConnection()));
    }
}
