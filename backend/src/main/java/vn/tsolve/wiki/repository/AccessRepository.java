package vn.tsolve.wiki.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;
import vn.tsolve.wiki.entity.WikiDocument;

public interface AccessRepository extends Repository<WikiDocument, UUID> {
    @Query(
            value = "SELECT s.space_id FROM space s WHERE s.public_id = :spaceId AND " + AccessSql.ACTIVE_SPACE
                    + " FOR UPDATE OF s",
            nativeQuery = true)
    Optional<Long> lockActiveSpace(@Param("spaceId") UUID spaceId);

    @Query(
            value =
                    "SELECT s.public_id FROM folder f JOIN space s ON s.space_id = f.space_id WHERE f.folder_id = :folderId",
            nativeQuery = true)
    Optional<UUID> findFolderSpace(@Param("folderId") UUID folderId);

    @Query(
            value = "SELECT EXISTS (SELECT 1 FROM space s WHERE s.space_id = :spaceId AND " + AccessSql.MANAGES_SPACE
                    + ")",
            nativeQuery = true)
    boolean managesSpace(@Param("userId") UUID userId, @Param("spaceId") long spaceId);

    @Query(
            value = "SELECT EXISTS (SELECT 1 FROM space s WHERE s.space_id = :spaceId AND " + AccessSql.VIEWABLE_SPACE
                    + ")",
            nativeQuery = true)
    boolean canViewSpace(@Param("userId") UUID userId, @Param("spaceId") long spaceId);

    @Query(
            value = "SELECT w.wiki_document_id FROM wiki_document w JOIN space s ON s.space_id = w.space_id "
                    + "WHERE w.wiki_document_id IN (:ids) AND " + AccessSql.VIEWABLE_DOCUMENT,
            nativeQuery = true)
    Set<UUID> findAvailableDocuments(@Param("userId") UUID userId, @Param("ids") Collection<UUID> ids);

    @Query(value = AccessSql.BEST_PASSAGES + AccessSql.VIEWABLE_DOCUMENT + AccessSql.RANK_PASSAGES, nativeQuery = true)
    List<PassageRow> nearestVisible(
            @Param("userId") UUID userId,
            @Param("vector") String vector,
            @Param("minSimilarity") double minSimilarity,
            @Param("limit") int limit);

    @Query(
            value = AccessSql.BEST_PASSAGES + AccessSql.PUBLISHED_DOCUMENT + " AND w.visibility = 'COMPANY_WIDE' "
                    + AccessSql.RANK_PASSAGES,
            nativeQuery = true)
    List<PassageRow> nearestCompanyWide(
            @Param("vector") String vector, @Param("minSimilarity") double minSimilarity, @Param("limit") int limit);

    @Query(
            value = "SELECT s.public_id AS \"spaceId\", s.name FROM space s WHERE " + AccessSql.MANAGES_SPACE
                    + " ORDER BY s.name, s.public_id",
            nativeQuery = true)
    List<SpaceRow> findManagedSpaces(@Param("userId") UUID userId);

    @Query(
            value = "SELECT s.public_id AS \"spaceId\", s.name FROM space s WHERE " + AccessSql.VIEWABLE_SPACE
                    + " ORDER BY s.name, s.public_id",
            nativeQuery = true)
    List<SpaceRow> findViewableSpaces(@Param("userId") UUID userId);

    interface PassageRow {
        UUID getDocumentId();

        String getTitle();

        String getText();

        double getSimilarity();
    }

    interface SpaceRow {
        UUID getSpaceId();

        String getName();
    }

    /** Shared predicates use the aliases s (Space) and w (document); access is always read from current rows. */
    final class AccessSql {
        public static final String ACTIVE_SPACE = """
                s.status = 'ACTIVE'
                AND EXISTS (SELECT 1 FROM department d WHERE d.department_id = s.department_id AND d.status = 'ACTIVE')
                """;

        public static final String VIEWABLE_SPACE = ACTIVE_SPACE + """
                AND EXISTS (SELECT 1 FROM app_user u WHERE u.id = :userId AND u.status = 'ACTIVE' AND (
                      EXISTS (SELECT 1 FROM space_manager sm JOIN user_role r ON r.user_id = sm.user_id AND r.role = 'KNOWLEDGE_MANAGER'
                              WHERE sm.space_id = s.space_id AND sm.user_id = u.id)
                   OR s.department_id IN (
                          SELECT u.department_id
                          UNION
                          SELECT pr.department_id FROM project_membership m JOIN project pr ON pr.public_id = m.project_id
                          WHERE m.user_id = u.id AND m.role_in_project = 'MEMBER'
                            AND EXISTS (SELECT 1 FROM user_role r WHERE r.user_id = u.id AND r.role = 'TICKET_RESOLVER'))
                   OR EXISTS (SELECT 1 FROM space_permission p WHERE p.space_id = s.space_id AND p.user_id = u.id)
                   OR EXISTS (SELECT 1 FROM space_permission p JOIN department pd ON pd.department_id = p.department_id AND pd.status = 'ACTIVE'
                              WHERE p.space_id = s.space_id AND p.department_id IN (
                          SELECT u.department_id
                          UNION
                          SELECT pr.department_id FROM project_membership m JOIN project pr ON pr.public_id = m.project_id
                          WHERE m.user_id = u.id AND m.role_in_project = 'MEMBER'
                            AND EXISTS (SELECT 1 FROM user_role r WHERE r.user_id = u.id AND r.role = 'TICKET_RESOLVER')))))
                """;

        public static final String PUBLISHED_DOCUMENT = "w.status = 'PUBLISHED' AND " + ACTIVE_SPACE;

        public static final String VIEWABLE_DOCUMENT = PUBLISHED_DOCUMENT + """
                AND EXISTS (SELECT 1 FROM app_user viewer WHERE viewer.id = :userId AND viewer.status = 'ACTIVE')
                AND (w.visibility = 'COMPANY_WIDE' OR (
                """ + VIEWABLE_SPACE + "))";

        public static final String MANAGES_SPACE = ACTIVE_SPACE + """
                AND EXISTS (SELECT 1 FROM space_manager sm
                    JOIN app_user u ON u.id = sm.user_id AND u.status = 'ACTIVE'
                    JOIN user_role r ON r.user_id = u.id AND r.role = 'KNOWLEDGE_MANAGER'
                    WHERE sm.space_id = s.space_id AND u.id = :userId)
                """;

        private static final String BEST_PASSAGES = """
                SELECT passages.* FROM (
                    SELECT DISTINCT ON (w.wiki_document_id)
                        w.wiki_document_id AS "documentId", w.title, c.text,
                        1 - (c.embedding <=> CAST(:vector AS vector)) AS similarity
                    FROM wiki_document w JOIN space s ON s.space_id = w.space_id
                    JOIN wiki_chunk c ON c.wiki_document_id = w.wiki_document_id
                    WHERE w.index_status = 'INDEXED' AND
                """;

        private static final String RANK_PASSAGES = """
                    ORDER BY w.wiki_document_id, c.embedding <=> CAST(:vector AS vector), c.position
                ) passages WHERE passages.similarity >= :minSimilarity
                ORDER BY passages.similarity DESC, passages."documentId" LIMIT :limit
                """;

        private AccessSql() {}
    }
}
