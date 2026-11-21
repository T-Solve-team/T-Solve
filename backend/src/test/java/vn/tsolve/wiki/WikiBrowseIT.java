package vn.tsolve.wiki;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.security.access.AccessDeniedException;
import vn.tsolve.common.exception.NotFoundException;
import vn.tsolve.wiki.dto.WikiDocumentSummary;
import vn.tsolve.wiki.dto.WikiFolder;
import vn.tsolve.wiki.dto.WikiSpace;
import vn.tsolve.wiki.entity.WikiDocumentType;

class WikiBrowseIT extends WikiReadTestSupport {
    @Test
    void listsOnlyViewableSpacesByNameAndComputesManagerAccess_AC_002_1_BUG_257() {
        manager();
        long alpha = space(department("Alpha"), "Alpha Wiki");
        space(department("Other"), "Other Wiki");
        assertThat(browse.listSpaces(user))
                .containsExactly(new WikiSpace(spaceId, "Support Wiki", "Support", "KNOWLEDGE_MANAGER"));
        jdbc.update(
                "INSERT INTO space_permission(space_id, user_id, granted_by, granted_at) VALUES (?, ?, ?, now())",
                alpha,
                user,
                user);
        assertThat(browse.listSpaces(user))
                .containsExactly(
                        new WikiSpace(publicId(alpha), "Alpha Wiki", "Alpha", "VIEW"),
                        new WikiSpace(spaceId, "Support Wiki", "Support", "KNOWLEDGE_MANAGER"));
        jdbc.update("DELETE FROM user_role WHERE user_id = ?", user);
        assertThat(browse.listSpaces(user).get(1).accessLevel()).isEqualTo("VIEW");
    }

    @Test
    void listsCompanyWideAcrossNonViewableSpacesBySpaceThenTitle_AC_002_2_AC_002_10() {
        long other = space(department("Other"), "Alpha Wiki");
        UUID z = document(other, null, "Zulu", "COMPANY_WIDE", "FAILED", "Text");
        UUID a = document(other, null, "Alpha", "COMPANY_WIDE", "PENDING", "Text");
        UUID b = document(space, null, "Beta", "COMPANY_WIDE", "INDEXED", "Text");
        document(other, null, "Private", "DEPARTMENT", "INDEXED", "Text");
        UUID trashed = document(other, null, "Trashed", "COMPANY_WIDE", "INDEXED", "Text");
        jdbc.update("UPDATE wiki_document SET status = 'DELETED' WHERE wiki_document_id = ?", trashed);
        assertThat(browse.listCompanyWide(user))
                .extracting(WikiDocumentSummary::documentId)
                .containsExactly(a, z, b);
        assertThat(browse.listCompanyWide(user).getFirst())
                .isEqualTo(new WikiDocumentSummary(
                        a,
                        "Alpha",
                        publicId(other),
                        "Alpha Wiki",
                        null,
                        null,
                        "COMPANY_WIDE",
                        WikiDocumentType.OTHER,
                        "Reader Name",
                        UPDATED));
    }

    @ParameterizedTest
    @ValueSource(strings = {"HIDDEN", "DEPARTMENT_DELETED"})
    void excludesInactiveScopesFromEveryRead_AC_002_1_AC_002_2_AC_002_6_AC_002_13(String state) {
        UUID id = document(space, null, "Guide", "COMPANY_WIDE", "INDEXED", "Text");
        if (state.equals("HIDDEN")) jdbc.update("UPDATE space SET status = 'HIDDEN' WHERE space_id = ?", space);
        else jdbc.update("UPDATE department SET status = 'DELETED' WHERE department_id = ?", department);
        assertThat(browse.listSpaces(user)).isEmpty();
        assertThat(browse.listCompanyWide(user)).isEmpty();
        assertMissingSpace(spaceId);
        assertThatThrownBy(() -> browse.getDocument(user, id))
                .isInstanceOfSatisfying(NotFoundException.class, error -> assertThat(error.getCode())
                        .isEqualTo("MSG110"));
    }

    @Test
    void listsFlatFoldersByNameIncludingAllLevels_AC_002_3() {
        UUID z = folder(space, null, "Zulu", 1);
        UUID a = folder(space, z, "Alpha", 2);
        UUID m = folder(space, a, "Middle", 3);
        folder(space(department("Other"), "Other Wiki"), null, "Unrelated", 1);
        assertThat(browse.listFolders(user, spaceId))
                .containsExactly(
                        new WikiFolder(a, z, "Alpha", 2),
                        new WikiFolder(m, a, "Middle", 3),
                        new WikiFolder(z, null, "Zulu", 1));
    }

    @Test
    void listsDirectPublishedDocumentsAtRootOrSelectedFolder_AC_002_4_AC_002_10() {
        UUID folder = folder(space, null, "Guides", 1);
        UUID child = folder(space, folder, "Child", 2);
        UUID rootZ = document(space, null, "Zulu", "DEPARTMENT", "FAILED", "Text");
        UUID rootA = document(space, null, "Alpha", "COMPANY_WIDE", "PENDING", "Text");
        UUID selected = document(space, folder, "Selected", "DEPARTMENT", "PENDING", "Text");
        document(space, child, "Nested", "DEPARTMENT", "INDEXED", "Text");
        UUID trashed = document(space, folder, "Trash", "DEPARTMENT", "INDEXED", "Text");
        jdbc.update("UPDATE wiki_document SET status = 'DELETED' WHERE wiki_document_id = ?", trashed);
        assertThat(browse.listDocuments(user, spaceId, null))
                .extracting(WikiDocumentSummary::documentId)
                .containsExactly(rootA, rootZ);
        assertThat(browse.listDocuments(user, spaceId, folder))
                .containsExactly(new WikiDocumentSummary(
                        selected,
                        "Selected",
                        spaceId,
                        "Support Wiki",
                        folder,
                        "Guides",
                        "DEPARTMENT",
                        WikiDocumentType.OTHER,
                        "Reader Name",
                        UPDATED));
    }

    @Test
    void deniesActiveNonViewableSpaceEvenWithCompanyWideDocument_AC_002_5() {
        long other = space(department("Other"), "Other Wiki");
        document(other, null, "Public", "COMPANY_WIDE", "PENDING", "Text");
        assertThatThrownBy(() -> browse.listFolders(user, publicId(other))).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> browse.listDocuments(user, publicId(other), null))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void rejectsUnknownSpaceAndUnknownOrForeignFolder_AC_002_6() {
        assertMissingSpace(UUID.randomUUID());
        UUID foreign = folder(space(department("Other"), "Other Wiki"), null, "Foreign", 1);
        for (UUID folder : new UUID[] {foreign, UUID.randomUUID()}) {
            assertThatThrownBy(() -> browse.listDocuments(user, spaceId, folder))
                    .isInstanceOfSatisfying(NotFoundException.class, error -> assertThat(error.getCode())
                            .isEqualTo("MSG04"));
        }
    }

    @Test
    void readsCompleteDetailAndOnlyCurrentManagerCanDelete_AC_002_11() {
        UUID folder = folder(space, null, "Guides", 1);
        UUID id = document(space, folder, "Guide", "DEPARTMENT", "PENDING", "Extracted text");
        var detail = browse.getDocument(user, id);
        assertThat(detail.documentId()).isEqualTo(id);
        assertThat(detail.spaceId()).isEqualTo(spaceId);
        assertThat(detail.spaceName()).isEqualTo("Support Wiki");
        assertThat(detail.folderId()).isEqualTo(folder);
        assertThat(detail.folderName()).isEqualTo("Guides");
        assertThat(detail.title()).isEqualTo("Guide");
        assertThat(detail.visibility()).isEqualTo("DEPARTMENT");
        assertThat(detail.owner()).isEqualTo("Reader Name");
        assertThat(detail.updatedAt()).isEqualTo(UPDATED);
        assertThat(detail.content()).isEqualTo("Extracted text");
        assertThat(detail.fileName()).isEqualTo("guide.pdf");
        assertThat(detail.fileSize()).isEqualTo(42);
        assertThat(detail.canDelete()).isFalse();
        assertThat(detail.inTrash()).isFalse();
        manager();
        assertThat(browse.getDocument(user, id).canDelete()).isTrue();
        jdbc.update("DELETE FROM user_role WHERE user_id = ?", user);
        assertThat(browse.getDocument(user, id).canDelete()).isFalse();
    }

    @Test
    void hidesGuessedUnknownTrashedPurgedAndRevokedDocuments_AC_002_13() {
        long other = space(department("Other"), "Other Wiki");
        UUID privateId = document(other, null, "Private", "DEPARTMENT", "INDEXED", "Text");
        UUID publicId = document(other, null, "Public", "COMPANY_WIDE", "PENDING", "Text");
        assertThat(browse.getDocument(user, publicId).canDelete()).isFalse();
        UUID trashed = document(space, null, "Trash", "DEPARTMENT", "INDEXED", "Text");
        jdbc.update("UPDATE wiki_document SET status = 'DELETED' WHERE wiki_document_id = ?", trashed);
        UUID purged = document(space, null, "Purged", "COMPANY_WIDE", "INDEXED", "Text");
        jdbc.update("DELETE FROM wiki_document WHERE wiki_document_id = ?", purged);
        for (UUID id : new UUID[] {privateId, trashed, purged, UUID.randomUUID()}) {
            assertThatThrownBy(() -> browse.getDocument(user, id))
                    .isInstanceOfSatisfying(NotFoundException.class, error -> assertThat(error.getCode())
                            .isEqualTo("MSG110"));
        }
        jdbc.update(
                "INSERT INTO space_permission(space_id, user_id, granted_by, granted_at) VALUES (?, ?, ?, now())",
                other,
                user,
                user);
        assertThat(browse.getDocument(user, privateId).documentId()).isEqualTo(privateId);
        jdbc.update("DELETE FROM space_permission WHERE user_id = ?", user);
        assertThatThrownBy(() -> browse.getDocument(user, privateId)).isInstanceOf(NotFoundException.class);
    }

    private void assertMissingSpace(UUID id) {
        assertThatThrownBy(() -> browse.listFolders(user, id))
                .isInstanceOfSatisfying(NotFoundException.class, error -> assertThat(error.getCode())
                        .isEqualTo("MSG04"));
        assertThatThrownBy(() -> browse.listDocuments(user, id, null))
                .isInstanceOfSatisfying(NotFoundException.class, error -> assertThat(error.getCode())
                        .isEqualTo("MSG04"));
    }
}
