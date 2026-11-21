package vn.tsolve.wiki;

import java.sql.Connection;
import java.time.Instant;
import java.util.Arrays;
import java.util.UUID;
import liquibase.Liquibase;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.ClassLoaderResourceAccessor;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import vn.tsolve.ticket.TicketTestDatabase;
import vn.tsolve.wiki.repository.WikiSearchRepository;
import vn.tsolve.wiki.service.impl.WikiBrowseServiceImpl;

abstract class WikiReadTestSupport {
    protected static JdbcTemplate jdbc;
    protected WikiSearchRepository repository;
    protected WikiBrowseServiceImpl browse;
    protected UUID user;
    protected long department;
    protected long space;
    protected UUID spaceId;
    protected static final Instant UPDATED = Instant.parse("2026-11-13T12:00:00Z");

    @BeforeAll
    static void migrate() throws Exception {
        var server = TicketTestDatabase.DB;
        var admin = new JdbcTemplate(
                new DriverManagerDataSource(server.getJdbcUrl(), server.getUsername(), server.getPassword()));
        String databaseName = "wiki_read_" + UUID.randomUUID().toString().replace("-", "");
        admin.execute("CREATE DATABASE " + databaseName);
        String url = server.getJdbcUrl();
        var source = new DriverManagerDataSource(
                url.substring(0, url.lastIndexOf('/') + 1) + databaseName, server.getUsername(), server.getPassword());
        jdbc = new JdbcTemplate(source);
        applyMigrations(source);
    }

    protected static void applyMigrations(javax.sql.DataSource source) throws Exception {
        try (Connection connection = source.getConnection();
                var resources = new ClassLoaderResourceAccessor();
                var liquibase = new Liquibase(
                        "db/changelog/db.changelog-master.xml",
                        resources,
                        DatabaseFactory.getInstance()
                                .findCorrectDatabaseImplementation(new JdbcConnection(connection)))) {
            liquibase.update("");
        }
    }

    @BeforeEach
    void prepare() {
        jdbc.execute("TRUNCATE department, app_user CASCADE");
        repository = new WikiSearchRepository(new NamedParameterJdbcTemplate(jdbc));
        browse = new WikiBrowseServiceImpl(repository);
        department = department("Support");
        space = space(department, "Support Wiki");
        spaceId = publicId(space);
        user = jdbc.queryForObject("""
                INSERT INTO app_user(email, full_name, department_id, status, created_at)
                VALUES ('reader@example.test', 'Reader Name', ?, 'ACTIVE', now()) RETURNING id
                """, UUID.class, department);
    }

    protected long department(String name) {
        return jdbc.queryForObject("INSERT INTO department(name) VALUES (?) RETURNING department_id", Long.class, name);
    }

    protected long space(long departmentId, String name) {
        return jdbc.queryForObject(
                "INSERT INTO space(department_id, name) VALUES (?, ?) RETURNING space_id",
                Long.class,
                departmentId,
                name);
    }

    protected UUID publicId(long internalId) {
        return jdbc.queryForObject("SELECT public_id FROM space WHERE space_id = ?", UUID.class, internalId);
    }

    protected UUID folder(long spaceId, UUID parent, String name, int level) {
        return jdbc.queryForObject("""
                INSERT INTO folder(space_id, parent_id, name, level, created_by, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, now(), now()) RETURNING folder_id
                """, UUID.class, spaceId, parent, name, level, user);
    }

    protected UUID document(
            long spaceId, UUID folderId, String title, String visibility, String indexStatus, String content) {
        return jdbc.queryForObject(
                """
                INSERT INTO wiki_document(space_id, folder_id, title, visibility, status, content, file_key, file_name,
                    file_size, content_type, word_count, index_status, owner_id, created_at, updated_at)
                VALUES (?, ?, ?, ?, 'PUBLISHED', ?, 'wiki/file', 'guide.pdf', 42, 'application/pdf', 2, ?, ?, now(), ?)
                RETURNING wiki_document_id
                """,
                UUID.class,
                spaceId,
                folderId,
                title,
                visibility,
                content,
                indexStatus,
                user,
                java.sql.Timestamp.from(UPDATED));
    }

    protected void manager() {
        jdbc.update("INSERT INTO user_role(user_id, role, assigned_at) VALUES (?, 'KNOWLEDGE_MANAGER', now())", user);
        jdbc.update("INSERT INTO space_manager(space_id, user_id, assigned_at) VALUES (?, ?, now())", space, user);
    }

    protected void chunk(UUID id, int position, float x, float y) {
        jdbc.update("""
                INSERT INTO wiki_chunk(wiki_document_id, position, text, embedding, model, created_at)
                VALUES (?, ?, 'Passage', CAST(? AS vector), 'bge-m3', now())
                """, id, position, Arrays.toString(vector(x, y)));
    }

    protected static float[] vector(float x, float y) {
        float[] values = new float[1024];
        values[0] = x;
        values[1] = y;
        return values;
    }
}
