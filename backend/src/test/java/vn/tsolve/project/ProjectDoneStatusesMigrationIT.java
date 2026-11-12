package vn.tsolve.project;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.ThrowingConsumer;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import vn.tsolve.identity.TwoTenantDatabases;

class ProjectDoneStatusesMigrationIT {

    @Test
    @DisplayName("AC-002.19 converts legacy Done statuses to singleton strings without changing other data")
    void preservesLegacyRows_AC_002_19() throws Throwable {
        List<String> sql = readChangeset();
        withScratchSchema(connection -> {
            List<Map<String, Object>> before = snapshot(connection);
            Map<String, String> legacyStatuses = new LinkedHashMap<>();
            before.forEach(row ->
                    legacyStatuses.put((String) row.get("jira_project_key"), (String) row.remove("done_status")));

            try (Statement statement = connection.createStatement()) {
                statement.execute(sql.get(0));
            }
            try (PreparedStatement statement = connection.prepareStatement("""
                    SELECT data_type FROM information_schema.columns
                    WHERE table_schema = ? AND table_name = 'project' AND column_name = 'done_status'
                    """)) {
                statement.setString(1, connection.getSchema());
                try (ResultSet result = statement.executeQuery()) {
                    assertThat(result.next()).isTrue();
                    assertThat(result.getString("data_type")).isEqualTo("jsonb");
                }
            }
            try (Statement statement = connection.createStatement();
                    ResultSet result = statement.executeQuery("""
                            SELECT jira_project_key, done_status IS NULL AS sql_null,
                                   done_status ->> 0 AS first_value,
                                   jsonb_array_length(done_status) AS array_length,
                                   jsonb_typeof(done_status -> 0) AS element_type,
                                   done_status = '["10002"]'::jsonb AS scalar_matches
                            FROM project ORDER BY project_id
                            """)) {
                while (result.next()) {
                    String key = result.getString("jira_project_key");
                    assertThat(legacyStatuses).containsKey(key);
                    String original = legacyStatuses.get(key);
                    assertThat(result.getBoolean("sql_null")).as(key).isEqualTo(original == null);
                    if (original != null) {
                        assertThat(result.getString("first_value")).as(key).isEqualTo(original);
                        assertThat(result.getInt("array_length")).as(key).isEqualTo(1);
                        assertThat(result.getString("element_type")).as(key).isEqualTo("string");
                        if (key.equals("SCALAR")) {
                            assertThat(result.getBoolean("scalar_matches")).isTrue();
                        }
                    }
                }
            }
            List<Map<String, Object>> after = snapshot(connection);
            after.forEach(row -> row.remove("done_status"));
            assertThat(after).hasSize(before.size()).isEqualTo(before);
        });
    }

    @Test
    @DisplayName("AC-002.19 includes changeset 029 exactly once and last in the master changelog")
    void includesChangesetLast_AC_002_19() throws Exception {
        URL url = getClass().getClassLoader().getResource("db/changelog/db.changelog-master.xml");
        assertThat(url).as("master changelog resource").isNotNull();
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        List<String> includes = new ArrayList<>();
        try (var input = url.openStream()) {
            NodeList nodes = factory.newDocumentBuilder().parse(input).getElementsByTagName("include");
            for (int i = 0; i < nodes.getLength(); i++) {
                includes.add(((Element) nodes.item(i)).getAttribute("file"));
            }
        }
        assertThat(includes).isNotEmpty();
        assertThat(includes.getLast())
                .as("last master changelog include")
                .isEqualTo("changes/029-project-done-statuses.sql");
        assertThat(includes).containsOnlyOnce("changes/029-project-done-statuses.sql");
    }

    @Test
    @DisplayName("AC-002.20 leaves review ranges nullable without a CHECK, default or backfill")
    void preservesUnrestrictedReviewRanges_AC_002_20() throws Throwable {
        List<String> sql = readChangeset();
        withScratchSchema(connection -> {
            List<Map<String, Object>> before = snapshot(connection);
            try (Statement statement = connection.createStatement()) {
                statement.execute(sql.get(0));
            }
            assertThat(snapshot(connection).stream()
                            .map(row -> row.get("review_range"))
                            .toList())
                    .containsExactlyElementsOf(
                            before.stream().map(row -> row.get("review_range")).toList())
                    .containsNull()
                    .contains(0, -1);
            try (Statement statement = connection.createStatement()) {
                statement.executeUpdate("""
                        INSERT INTO project (department_id, jira_project_key, review_range)
                        VALUES (201, 'NEW_ZERO', 0), (202, 'NEW_NEGATIVE', -1)
                        """);
                statement.executeUpdate("""
                        INSERT INTO project (department_id, jira_project_key) VALUES (203, 'NEW_OMITTED')
                        """);
                try (ResultSet result = statement.executeQuery("""
                        SELECT jira_project_key, review_range FROM project
                        WHERE jira_project_key LIKE 'NEW_%' ORDER BY project_id
                        """)) {
                    assertThat(result.next()).isTrue();
                    assertThat(result.getString("jira_project_key")).isEqualTo("NEW_ZERO");
                    assertThat(result.getObject("review_range")).isEqualTo(0);
                    assertThat(result.next()).isTrue();
                    assertThat(result.getString("jira_project_key")).isEqualTo("NEW_NEGATIVE");
                    assertThat(result.getObject("review_range")).isEqualTo(-1);
                    assertThat(result.next()).isTrue();
                    assertThat(result.getString("jira_project_key")).isEqualTo("NEW_OMITTED");
                    assertThat(result.getObject("review_range")).isNull();
                    assertThat(result.next()).isFalse();
                }
            }
            try (PreparedStatement statement = connection.prepareStatement("""
                    SELECT column_default, is_nullable FROM information_schema.columns
                    WHERE table_schema = ? AND table_name = 'project' AND column_name = 'review_range'
                    """)) {
                statement.setString(1, connection.getSchema());
                try (ResultSet result = statement.executeQuery()) {
                    assertThat(result.next()).isTrue();
                    assertThat(result.getString("column_default")).isNull();
                    assertThat(result.getString("is_nullable")).isEqualTo("YES");
                }
            }
            try (PreparedStatement statement = connection.prepareStatement("""
                    SELECT count(*) FROM pg_constraint WHERE contype = 'c' AND conrelid = ?::regclass
                    """)) {
                statement.setString(1, connection.getSchema() + ".project");
                try (ResultSet result = statement.executeQuery()) {
                    assertThat(result.next()).isTrue();
                    assertThat(result.getLong(1)).isZero();
                }
            }
        });
    }

    @Test
    @DisplayName("AC-002.19 restores exact legacy VARCHAR values on rollback and singleton arrays on reapply")
    void rollsBackAndReapplies_AC_002_19() throws Throwable {
        List<String> sql = readChangeset();
        withScratchSchema(connection -> {
            List<Map<String, Object>> legacy = snapshot(connection);
            try (Statement statement = connection.createStatement()) {
                statement.execute(sql.get(0));
                List<Map<String, Object>> migrated = snapshot(connection);
                statement.execute(sql.get(1));
                assertThat(snapshot(connection)).isEqualTo(legacy);
                try (PreparedStatement column = connection.prepareStatement("""
                        SELECT data_type, character_maximum_length FROM information_schema.columns
                        WHERE table_schema = ? AND table_name = 'project' AND column_name = 'done_status'
                        """)) {
                    column.setString(1, connection.getSchema());
                    try (ResultSet result = column.executeQuery()) {
                        assertThat(result.next()).isTrue();
                        assertThat(result.getString("data_type")).isEqualTo("character varying");
                        assertThat(result.getInt("character_maximum_length")).isEqualTo(100);
                    }
                }
                statement.execute(sql.get(0));
                assertThat(snapshot(connection)).isEqualTo(migrated);
            }
        });
    }

    @Test
    @DisplayName("AC-002.19 rollback of several Done statuses retains only element zero")
    void documentsLossyRollback_AC_002_19() throws Throwable {
        // Rollback keeps element zero only, so it is for development / scratch databases.
        List<String> sql = readChangeset();
        withScratchSchema(connection -> {
            try (Statement statement = connection.createStatement()) {
                statement.execute(sql.get(0));
                assertThat(statement.executeUpdate("""
                                UPDATE project SET done_status = '["10001","10002","10003"]'::jsonb
                                WHERE jira_project_key = 'SCALAR'
                                """)).isEqualTo(1);
                statement.execute(sql.get(1));
                try (ResultSet result =
                        statement.executeQuery("SELECT done_status FROM project WHERE jira_project_key = 'SCALAR'")) {
                    assertThat(result.next()).isTrue();
                    assertThat(result.getString("done_status")).isEqualTo("10001");
                }
            }
        });
    }

    private void withScratchSchema(ThrowingConsumer<Connection> work) throws Throwable {
        String schema = "mig029_" + UUID.randomUUID().toString().replace("-", "");
        try (Connection connection = TwoTenantDatabases.main().getDataSource().getConnection();
                Statement statement = connection.createStatement()) {
            statement.execute("CREATE SCHEMA " + schema);
            try {
                statement.execute("SET search_path TO " + schema);
                statement.execute("""
                        CREATE TABLE project (
                            project_id BIGSERIAL PRIMARY KEY,
                            public_id UUID NOT NULL DEFAULT gen_random_uuid() UNIQUE,
                            department_id BIGINT NOT NULL,
                            jira_project_key VARCHAR(20) NOT NULL UNIQUE,
                            field_mapping JSONB,
                            done_status VARCHAR(100),
                            review_range INT,
                            status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE'
                        )
                        """);
                statement.executeUpdate("""
                        INSERT INTO project (public_id, department_id, jira_project_key, field_mapping,
                                             done_status, review_range, status)
                        VALUES ('00000000-0000-0000-0000-000000000001', 101, 'SCALAR', '{"summary":"name"}',
                                '10002', NULL, 'ACTIVE'),
                               ('00000000-0000-0000-0000-000000000002', 102, 'ARRAY_TEXT', NULL,
                                '["10001","10002"]', 0, 'REMOVED'),
                               ('00000000-0000-0000-0000-000000000003', 103, 'OBJECT_TEXT', '{"type":"kind"}',
                                '{"id":"3"}', -1, 'ACTIVE'),
                               ('00000000-0000-0000-0000-000000000004', 104, 'SQL_NULL', '{"issue":"key"}',
                                NULL, 3, 'REMOVED')
                        """);
                work.accept(connection);
            } finally {
                try {
                    statement.execute("DROP SCHEMA " + schema + " CASCADE");
                } finally {
                    statement.execute("RESET search_path");
                }
            }
        }
    }

    private List<String> readChangeset() throws Exception {
        URL url = getClass().getClassLoader().getResource("db/changelog/changes/029-project-done-statuses.sql");
        assertThat(url).as("029 changeset resource").isNotNull();
        StringBuilder forward = new StringBuilder();
        String rollback = "";
        boolean afterChangeset = false;
        try (BufferedReader reader =
                new BufferedReader(new InputStreamReader(url.openStream(), StandardCharsets.UTF_8))) {
            for (String line; (line = reader.readLine()) != null; ) {
                String trimmed = line.trim();
                if (trimmed.startsWith("--changeset ")) {
                    afterChangeset = true;
                } else if (trimmed.startsWith("--rollback ")) {
                    rollback = trimmed.substring("--rollback ".length());
                    break;
                } else if (afterChangeset && !trimmed.isEmpty() && !trimmed.startsWith("--")) {
                    forward.append(trimmed).append(' ');
                }
            }
        }
        assertThat(forward.toString()).as("029 forward statement").isNotBlank();
        assertThat(rollback).as("029 rollback statement").isNotBlank();
        return List.of(forward.toString().trim(), rollback);
    }

    private List<Map<String, Object>> snapshot(Connection connection) throws Exception {
        List<Map<String, Object>> rows = new ArrayList<>();
        try (Statement statement = connection.createStatement();
                ResultSet result = statement.executeQuery("""
                        SELECT project_id, public_id, department_id, jira_project_key,
                               field_mapping::text AS field_mapping, done_status::text AS done_status,
                               review_range, status
                        FROM project ORDER BY project_id
                        """)) {
            while (result.next()) {
                Map<String, Object> row = new LinkedHashMap<>();
                for (int i = 1; i <= result.getMetaData().getColumnCount(); i++) {
                    row.put(result.getMetaData().getColumnLabel(i), result.getObject(i));
                }
                rows.add(row);
            }
        }
        return rows;
    }
}
