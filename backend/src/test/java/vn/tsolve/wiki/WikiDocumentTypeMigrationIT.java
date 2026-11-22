package vn.tsolve.wiki;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

class WikiDocumentTypeMigrationIT extends WikiReadTestSupport {
    @Test
    void backfillsExistingDocumentsAndPreservesIndexAndPassages_AC_007_9() throws Exception {
        // Recreate the schema and migration history immediately before this changeset.
        jdbc.execute("ALTER TABLE wiki_document DROP COLUMN document_type");
        jdbc.update("DELETE FROM databasechangelog WHERE id = '049-wiki-document-type'");
        var indexed = document(space, null, "Indexed", "DEPARTMENT", "INDEXED", "Indexed text");
        var pending = document(space, null, "Pending", "DEPARTMENT", "PENDING", "Pending text");
        var failed = document(space, null, "Failed", "DEPARTMENT", "FAILED", "Failed text");
        chunk(indexed, 0, 1, 0);
        String passages = "SELECT wiki_chunk_id, position, text, embedding::text, model, created_at FROM wiki_chunk";
        var before = jdbc.queryForList(passages);
        applyMigrations(jdbc.getDataSource());

        assertThat(jdbc.queryForList("SELECT DISTINCT document_type FROM wiki_document", String.class))
                .containsExactly("OTHER");
        assertThat(jdbc.queryForObject(
                        "SELECT index_status FROM wiki_document WHERE wiki_document_id = ?", String.class, indexed))
                .isEqualTo("INDEXED");
        assertThat(jdbc.queryForObject(
                        "SELECT index_status FROM wiki_document WHERE wiki_document_id = ?", String.class, pending))
                .isEqualTo("PENDING");
        assertThat(jdbc.queryForObject(
                        "SELECT index_status FROM wiki_document WHERE wiki_document_id = ?", String.class, failed))
                .isEqualTo("FAILED");
        assertThat(jdbc.queryForList(passages)).isEqualTo(before);
        assertThatThrownBy(() -> jdbc.update("UPDATE wiki_document SET document_type = 'UNKNOWN'"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
