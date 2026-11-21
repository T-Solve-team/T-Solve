package vn.tsolve.wiki;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mockito;
import vn.tsolve.ai.client.EmbeddingClient;
import vn.tsolve.wiki.dto.WikiDocumentSummary;
import vn.tsolve.wiki.entity.WikiDocumentType;
import vn.tsolve.wiki.service.impl.WikiSearchServiceImpl;

class WikiSearchIT extends WikiReadTestSupport {
    private WikiSearchServiceImpl search(boolean offline, double threshold) {
        EmbeddingClient embeddings = Mockito.mock(EmbeddingClient.class);
        if (offline) when(embeddings.embed(any())).thenThrow(new IllegalStateException("offline"));
        else when(embeddings.embed(any())).thenReturn(List.of(vector(1, 0)));
        return new WikiSearchServiceImpl(repository, embeddings, threshold);
    }

    @Test
    void combinesKeywordRankAndBestPassageSimilarityWithoutDuplicates_AC_002_7_AC_002_10_AC_007_10() {
        UUID both = document(space, null, "Guide", "DEPARTMENT", "INDEXED", "repair");
        UUID semantic = document(space, null, "Meaning", "DEPARTMENT", "INDEXED", "unrelated");
        UUID keyword = document(space, null, "Keyword", "DEPARTMENT", "PENDING", "repair");
        UUID below = document(space, null, "Below", "DEPARTMENT", "INDEXED", "unrelated");
        jdbc.update("UPDATE wiki_document SET document_type = 'POLICY' WHERE wiki_document_id = ?", both);
        jdbc.update("UPDATE wiki_document SET document_type = 'GUIDE' WHERE wiki_document_id = ?", semantic);
        chunk(both, 0, 0, 1);
        chunk(both, 1, 1, 0);
        chunk(semantic, 0, 0.8f, 0.6f);
        chunk(below, 0, 0, 1);
        var result = search(false, 0.55).search(user, "repair");
        assertThat(result).extracting(WikiDocumentSummary::documentId).containsExactly(both, semantic, keyword);
        assertThat(result.getFirst())
                .isEqualTo(new WikiDocumentSummary(
                        both,
                        "Guide",
                        spaceId,
                        "Support Wiki",
                        null,
                        null,
                        "DEPARTMENT",
                        WikiDocumentType.POLICY,
                        "Reader Name",
                        UPDATED));
        assertThat(search(false, 0.9).search(user, "repair"))
                .extracting(WikiDocumentSummary::documentId)
                .containsExactly(both, keyword);
    }

    @Test
    void storesTextBeyondTheTsvectorLimitAndStillFindsItsStart_BUG_291() {
        // ~1.9 MB of distinct words: indexed whole, the tsvector would exceed PostgreSQL's 1 MB cap.
        StringBuilder text = new StringBuilder("repair");
        for (int i = 0; text.length() < 1_900_000; i++) text.append(" w").append(Integer.toString(i, 36));
        UUID id = document(space, null, "Large", "DEPARTMENT", "PENDING", text.toString());
        assertThat(search(true, 0.55).search(user, "repair"))
                .extracting(WikiDocumentSummary::documentId)
                .containsExactly(id);
    }

    @Test
    void breaksEqualScoresByTitleAndCapsAtFifty_AC_002_7() {
        for (int i = 54; i >= 0; i--)
            document(space, null, String.format("Guide %02d", i), "DEPARTMENT", "PENDING", "repair");
        var result = search(true, 0.55).search(user, "repair");
        assertThat(result).hasSize(50);
        assertThat(result).extracting(WikiDocumentSummary::title).isSorted();
        assertThat(result.getFirst().title()).isEqualTo("Guide 00");
        assertThat(result.getLast().title()).isEqualTo("Guide 49");
    }

    @Test
    void fallsBackToKeywordMatchesInTitleAndTextWhenEmbeddingThrows_AC_002_8() {
        UUID title = document(space, null, "repair", "DEPARTMENT", "FAILED", "unrelated");
        UUID body = document(space, null, "Body", "DEPARTMENT", "PENDING", "repair");
        UUID semantic = document(space, null, "Semantic", "COMPANY_WIDE", "INDEXED", "unrelated");
        chunk(semantic, 0, 1, 0);
        assertThat(search(true, 0.55).search(user, "repair"))
                .extracting(WikiDocumentSummary::documentId)
                .containsExactlyInAnyOrder(title, body);
    }

    @ParameterizedTest
    @ValueSource(strings = {"PENDING", "FAILED"})
    void excludesStaleChunksButKeepsKeywordMatches_AC_002_7_AC_002_8(String status) {
        UUID id = document(space, null, "Guide", "DEPARTMENT", status, "repair");
        chunk(id, 0, 1, 0);
        assertThat(search(false, 0.55).search(user, "unrelated")).isEmpty();
        assertThat(search(false, 0.55).search(user, "repair"))
                .extracting(WikiDocumentSummary::documentId)
                .containsExactly(id);
    }

    @Test
    void filtersAccessStatusAndDeletedDepartmentsForBothSearchPaths_AC_002_7_AC_002_8_BUG_258() {
        long other = space(department("Other"), "Other Wiki");
        long hidden = space(department("Hidden"), "Hidden Wiki");
        long deletedDepartment = department("Deleted");
        long deleted = space(deletedDepartment, "Deleted Wiki");
        UUID publicId = document(other, null, "Public", "COMPANY_WIDE", "INDEXED", "repair");
        UUID privateId = document(other, null, "Private", "DEPARTMENT", "INDEXED", "repair");
        UUID hiddenId = document(hidden, null, "Hidden", "COMPANY_WIDE", "INDEXED", "repair");
        UUID deletedId = document(deleted, null, "Deleted", "COMPANY_WIDE", "INDEXED", "repair");
        UUID trash = document(space, null, "Trash", "COMPANY_WIDE", "INDEXED", "repair");
        for (UUID id : List.of(publicId, privateId, hiddenId, deletedId, trash)) chunk(id, 0, 1, 0);
        jdbc.update("UPDATE space SET status = 'HIDDEN' WHERE space_id = ?", hidden);
        jdbc.update("UPDATE department SET status = 'DELETED' WHERE department_id = ?", deletedDepartment);
        jdbc.update("UPDATE wiki_document SET status = 'DELETED' WHERE wiki_document_id = ?", trash);
        for (boolean offline : List.of(false, true)) {
            assertThat(search(offline, 0.55).search(user, "repair"))
                    .extracting(WikiDocumentSummary::documentId)
                    .containsExactly(publicId);
        }
    }

    @Test
    void safelyAcceptsSqlLikeAndWebsearchPunctuation_AC_002_9() {
        UUID id = document(space, null, "Guide", "COMPANY_WIDE", "PENDING", "repair");
        assertThat(search(true, 0.55).search(user, "'); DROP TABLE wiki_document; --"))
                .isEmpty();
        assertThat(search(true, 0.55).search(user, "\"repair\" -missing"))
                .extracting(WikiDocumentSummary::documentId)
                .containsExactly(id);
        assertThat(search(true, 0.55).search(user, "repair")).hasSize(1);
    }
}
