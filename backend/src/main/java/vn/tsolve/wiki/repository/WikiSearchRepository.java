package vn.tsolve.wiki.repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import vn.tsolve.wiki.dto.WikiDocumentDetail;
import vn.tsolve.wiki.dto.WikiDocumentSummary;
import vn.tsolve.wiki.dto.WikiFolder;
import vn.tsolve.wiki.dto.WikiSpace;
import vn.tsolve.wiki.dto.WikiTrashItem;
import vn.tsolve.wiki.repository.AccessRepository.AccessSql;

/** Read projections join identity names without loading or exposing entities from another module (W10). */
@Repository
@Transactional(readOnly = true)
public class WikiSearchRepository {
    private static final String SUMMARY_COLUMNS = """
            w.wiki_document_id, w.title, s.public_id AS space_public_id, s.name AS space_name,
            w.folder_id, f.name AS folder_name, w.visibility, o.full_name AS owner, w.updated_at
            """;
    private static final String DOCUMENT_JOINS = """
            FROM wiki_document w JOIN space s ON s.space_id = w.space_id
            JOIN app_user o ON o.id = w.owner_id
            LEFT JOIN folder f ON f.folder_id = w.folder_id
            """;
    private final NamedParameterJdbcTemplate jdbc;

    public WikiSearchRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<WikiSpace> listSpaces(UUID userId) {
        return jdbc.query(
                "SELECT s.public_id, s.name, d.name AS department_name, CASE WHEN (" + AccessSql.MANAGES_SPACE
                        + ") THEN 'KNOWLEDGE_MANAGER' ELSE 'VIEW' END AS access_level "
                        + "FROM space s JOIN department d ON d.department_id = s.department_id WHERE "
                        + AccessSql.VIEWABLE_SPACE
                        + " ORDER BY s.name, s.public_id",
                Map.of("userId", userId),
                (row, index) -> new WikiSpace(
                        row.getObject("public_id", UUID.class), row.getString("name"),
                        row.getString("department_name"), row.getString("access_level")));
    }

    public Optional<UUID> findDocumentSpace(UUID documentId) {
        return jdbc
                .query(
                        "SELECT s.public_id FROM wiki_document w JOIN space s ON s.space_id = w.space_id "
                                + "WHERE w.wiki_document_id = :documentId",
                        Map.of("documentId", documentId),
                        (row, index) -> row.getObject("public_id", UUID.class))
                .stream()
                .findFirst();
    }

    public List<WikiTrashItem> listTrash(long spaceId) {
        return jdbc.query(
                "SELECT w.wiki_document_id, w.title, f.name AS folder_name, o.full_name AS owner, "
                        + "actor.full_name AS deleted_by, w.deleted_at " + DOCUMENT_JOINS
                        + " JOIN app_user actor ON actor.id = w.deleted_by "
                        + "WHERE w.space_id = :spaceId AND w.status = 'DELETED' ORDER BY w.deleted_at DESC, w.wiki_document_id",
                Map.of("spaceId", spaceId),
                (row, index) -> new WikiTrashItem(
                        row.getObject("wiki_document_id", UUID.class),
                        row.getString("title"),
                        row.getString("folder_name"),
                        row.getString("owner"),
                        row.getString("deleted_by"),
                        row.getTimestamp("deleted_at").toInstant()));
    }

    public Optional<WikiDocumentDetail> findTrashedDocument(UUID userId, UUID documentId) {
        return findDocument(userId, documentId, "w.status = 'DELETED' AND " + AccessSql.MANAGES_SPACE, "false");
    }

    public Optional<SpaceAccess> findSpace(UUID userId, UUID spaceId) {
        return jdbc
                .query(
                        "SELECT s.space_id, (" + AccessSql.VIEWABLE_SPACE + ") AS viewable "
                                + "FROM space s WHERE s.public_id = :spaceId AND " + AccessSql.ACTIVE_SPACE,
                        Map.of("userId", userId, "spaceId", spaceId),
                        (row, index) -> new SpaceAccess(row.getLong("space_id"), row.getBoolean("viewable")))
                .stream()
                .findFirst();
    }

    public List<WikiDocumentSummary> listCompanyWide(UUID userId) {
        return jdbc.query(
                "SELECT " + SUMMARY_COLUMNS + DOCUMENT_JOINS + " WHERE " + AccessSql.VIEWABLE_DOCUMENT
                        + " AND w.visibility = 'COMPANY_WIDE' ORDER BY s.name, w.title, w.wiki_document_id",
                Map.of("userId", userId),
                (row, index) -> summary(row));
    }

    public List<WikiFolder> listFolders(long spaceId) {
        return jdbc.query(
                "SELECT folder_id, parent_id, name, level FROM folder WHERE space_id = :spaceId ORDER BY name, folder_id",
                Map.of("spaceId", spaceId),
                (row, index) -> new WikiFolder(
                        row.getObject("folder_id", UUID.class),
                        row.getObject("parent_id", UUID.class),
                        row.getString("name"),
                        row.getInt("level")));
    }

    public boolean isFolderInSpace(long spaceId, UUID folderId) {
        return Boolean.TRUE.equals(
                jdbc.queryForObject("""
                SELECT EXISTS (SELECT 1 FROM folder WHERE space_id = :spaceId AND folder_id = :folderId)
                """, Map.of("spaceId", spaceId, "folderId", folderId), Boolean.class));
    }

    public List<WikiDocumentSummary> listDocuments(UUID userId, long spaceId, UUID folderId) {
        var params = new MapSqlParameterSource("userId", userId)
                .addValue("spaceId", spaceId)
                .addValue("folderId", folderId);
        return jdbc.query(
                "SELECT " + SUMMARY_COLUMNS + DOCUMENT_JOINS + " WHERE " + AccessSql.VIEWABLE_DOCUMENT
                        + " AND w.space_id = :spaceId AND "
                        + (folderId == null ? "w.folder_id IS NULL" : "w.folder_id = :folderId")
                        + " ORDER BY w.title, w.wiki_document_id",
                params,
                (row, index) -> summary(row));
    }

    public Optional<WikiDocumentDetail> findDocument(UUID userId, UUID documentId) {
        return findDocument(userId, documentId, AccessSql.VIEWABLE_DOCUMENT, "(" + AccessSql.MANAGES_SPACE + ")");
    }

    private Optional<WikiDocumentDetail> findDocument(
            UUID userId, UUID documentId, String predicate, String canDelete) {
        return jdbc
                .query(
                        "SELECT " + SUMMARY_COLUMNS + ", w.content, w.file_name, w.file_size, "
                                + canDelete + " AS can_delete " + DOCUMENT_JOINS
                                + " WHERE w.wiki_document_id = :documentId AND " + predicate,
                        Map.of("userId", userId, "documentId", documentId),
                        (row, index) -> {
                            var item = summary(row);
                            return new WikiDocumentDetail(
                                    item.documentId(),
                                    item.title(),
                                    item.spaceId(),
                                    item.spaceName(),
                                    item.folderId(),
                                    item.folderName(),
                                    item.visibility(),
                                    item.owner(),
                                    item.updatedAt(),
                                    row.getString("content"),
                                    row.getString("file_name"),
                                    row.getLong("file_size"),
                                    row.getBoolean("can_delete"));
                        })
                .stream()
                .findFirst();
    }

    public List<WikiDocumentSummary> search(UUID userId, String query, String vector, double minSimilarity) {
        var params = new MapSqlParameterSource("userId", userId)
                .addValue("q", query)
                .addValue("vector", vector)
                .addValue("minSimilarity", minSimilarity);
        // With no embedding, omit the chunk scan and similarity entirely: keyword rank alone decides the order.
        String similarity = vector == null ? "0" : "COALESCE(best.similarity, 0)";
        String passages = vector == null ? "" : """
                LEFT JOIN LATERAL (
                    SELECT MAX(1 - (c.embedding <=> CAST(:vector AS vector))) AS similarity
                    FROM wiki_chunk c WHERE c.wiki_document_id = w.wiki_document_id AND w.index_status = 'INDEXED'
                ) best ON true
                """;
        String match = vector == null ? "" : " OR best.similarity >= :minSimilarity";
        return jdbc.query(
                "WITH query AS (SELECT websearch_to_tsquery('simple', :q) AS terms) SELECT "
                        + SUMMARY_COLUMNS + DOCUMENT_JOINS + " CROSS JOIN query " + passages + " WHERE "
                        + AccessSql.VIEWABLE_DOCUMENT
                        + " AND (w.search_vector @@ query.terms" + match
                        + ") ORDER BY ts_rank_cd(w.search_vector, query.terms) + "
                        + similarity + " DESC, w.title, w.wiki_document_id LIMIT 50",
                params,
                (row, index) -> summary(row));
    }

    private static WikiDocumentSummary summary(ResultSet row) throws SQLException {
        return new WikiDocumentSummary(
                row.getObject("wiki_document_id", UUID.class),
                row.getString("title"),
                row.getObject("space_public_id", UUID.class),
                row.getString("space_name"),
                row.getObject("folder_id", UUID.class),
                row.getString("folder_name"),
                row.getString("visibility"),
                row.getString("owner"),
                row.getTimestamp("updated_at").toInstant());
    }

    public record SpaceAccess(long spaceId, boolean viewable) {}
}
