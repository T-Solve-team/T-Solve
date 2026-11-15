package vn.tsolve.wiki.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import vn.tsolve.common.audit.AuditAction;
import vn.tsolve.common.audit.AuditService;
import vn.tsolve.common.exception.BusinessRuleException;
import vn.tsolve.common.exception.NotFoundException;
import vn.tsolve.platform.service.FileStorage;
import vn.tsolve.wiki.dto.WikiDocumentDetail;
import vn.tsolve.wiki.dto.WikiTrashItem;
import vn.tsolve.wiki.entity.WikiDocument;
import vn.tsolve.wiki.entity.WikiDocument.IndexStatus;
import vn.tsolve.wiki.entity.WikiDocument.Status;
import vn.tsolve.wiki.repository.AccessRepository;
import vn.tsolve.wiki.repository.WikiDocumentRepository;
import vn.tsolve.wiki.repository.WikiSearchRepository;
import vn.tsolve.worker.service.TenantJobService;

class WikiTrashServiceImplTest {
    private final WikiDocumentRepository documents = mock(WikiDocumentRepository.class);
    private final AccessRepository access = mock(AccessRepository.class);
    private final WikiSearchRepository search = mock(WikiSearchRepository.class);
    private final FileStorage storage = mock(FileStorage.class);
    private final AuditService audit = mock(AuditService.class);
    private final TenantJobService jobs = mock(TenantJobService.class);
    private final WikiTrashServiceImpl service =
            new WikiTrashServiceImpl(documents, access, search, storage, audit, jobs);
    private final UUID user = UUID.randomUUID();
    private final UUID space = UUID.randomUUID();
    private final UUID id = UUID.randomUUID();
    private final UUID folder = UUID.randomUUID();
    private WikiDocument document;

    @BeforeEach
    void setup() {
        document = new WikiDocument(
                42,
                folder,
                "Guide",
                WikiDocument.Visibility.DEPARTMENT,
                "Text",
                "wiki/file",
                "guide.pdf",
                42,
                "application/pdf",
                1,
                user,
                Instant.EPOCH);
        ReflectionTestUtils.setField(document, "id", id);
        when(documents.findForIndexing(id)).thenReturn(Optional.of(document));
        when(access.managesSpace(user, 42)).thenReturn(true);
        when(search.findDocumentSpace(id)).thenReturn(Optional.of(space));
        when(search.findSpace(user, space)).thenReturn(Optional.of(new WikiSearchRepository.SpaceAccess(42, true)));
        TransactionSynchronizationManager.initSynchronization();
    }

    @AfterEach
    void cleanup() {
        TransactionSynchronizationManager.clearSynchronization();
    }

    @Test
    void allowsManagerReadOnlyTrashDetailAndHidesItOtherwise_AC_005_4() {
        var detail = new WikiDocumentDetail(
                id,
                "Guide",
                space,
                "Wiki",
                folder,
                "Guides",
                "DEPARTMENT",
                "Owner",
                Instant.EPOCH,
                "Text",
                "guide.pdf",
                42,
                false);
        var browse = new WikiBrowseServiceImpl(search);
        when(search.findDocument(user, id)).thenReturn(Optional.empty());
        when(search.findTrashedDocument(user, id)).thenReturn(Optional.of(detail));
        assertThat(browse.getDocument(user, id)).isEqualTo(detail);
        assertThat(browse.getDocument(user, id).canDelete()).isFalse();
        when(search.findTrashedDocument(user, id)).thenReturn(Optional.empty());
        assertMissing(() -> browse.getDocument(user, id));
    }

    @Test
    void marksTrashWithActorAndTimeAndRetainsIndexAndFile_AC_005_1_AC_005_11() {
        ReflectionTestUtils.setField(document, "indexStatus", IndexStatus.INDEXED);
        Instant before = Instant.now();
        service.trash(user, id);
        assertThat(document.getStatus()).isEqualTo(Status.DELETED);
        assertThat(document.getDeletedBy()).isEqualTo(user);
        assertThat(document.getDeletedAt()).isBetween(before, Instant.now());
        assertThat(document.getUpdatedAt()).isEqualTo(document.getDeletedAt());
        assertThat(document.getIndexStatus()).isEqualTo(IndexStatus.INDEXED);
        verify(documents).saveAndFlush(document);
        verify(audit).record(AuditAction.DELETE, "WIKI_DOCUMENT", id, details("TRASH", "Guide"));
        assertThat(TransactionSynchronizationManager.getSynchronizations()).isEmpty();
        verifyNoInteractions(storage, jobs);
    }

    @Test
    void listsOnlyManagedSpaceTrash_AC_005_3() {
        var item = new WikiTrashItem(id, "Guide", null, "Owner", "Manager", Instant.EPOCH);
        when(search.listTrash(42)).thenReturn(List.of(item));
        assertThat(service.listTrash(user, space)).containsExactly(item);
        when(access.managesSpace(user, 42)).thenReturn(false);
        assertThatThrownBy(() -> service.listTrash(user, space)).isInstanceOf(AccessDeniedException.class);
        when(search.findSpace(user, space)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.listTrash(user, space))
                .isInstanceOfSatisfying(NotFoundException.class, error -> assertThat(error.getCode())
                        .isEqualTo("MSG04"));
    }

    @Test
    void restoresInOriginalFolderAndClearsDeletionMetadata_AC_005_5_AC_005_11() {
        deleted();
        var result = service.restore(user, id);
        assertThat(result.title()).isEqualTo("Guide");
        assertThat(result.renamed()).isFalse();
        assertThat(document.getStatus()).isEqualTo(Status.PUBLISHED);
        assertThat(document.getFolderId()).isEqualTo(folder);
        assertThat(document.getDeletedBy()).isNull();
        assertThat(document.getDeletedAt()).isNull();
        verify(audit).record(AuditAction.PUBLISH, "WIKI_DOCUMENT", id, details("RESTORE", "Guide"));
    }

    @Test
    void skipsTakenRestoredSuffixes_AC_005_6() {
        deleted();
        when(documents.existsBySpaceIdAndFolderIdAndStatusAndTitleIgnoreCase(42L, folder, Status.PUBLISHED, "Guide"))
                .thenReturn(true);
        when(documents.existsBySpaceIdAndFolderIdAndStatusAndTitleIgnoreCase(
                        42L, folder, Status.PUBLISHED, "Guide (restored)"))
                .thenReturn(true);
        var result = service.restore(user, id);
        assertThat(result.title()).isEqualTo("Guide (restored 2)");
        assertThat(result.renamed()).isTrue();
        assertThat(document.getTitle()).isEqualTo(result.title());
    }

    @Test
    void shortensTwoHundredCharacterTitleBeforeAppendingSuffix_AC_005_6() {
        deleted();
        ReflectionTestUtils.setField(document, "title", "x".repeat(200));
        when(documents.existsBySpaceIdAndFolderIdAndStatusAndTitleIgnoreCase(
                        42L, folder, Status.PUBLISHED, "x".repeat(200)))
                .thenReturn(true);
        var result = service.restore(user, id);
        assertThat(result.title()).isEqualTo("x".repeat(189) + " (restored)").hasSize(200);
        assertThat(result.renamed()).isTrue();
    }

    @ParameterizedTest
    @EnumSource(IndexStatus.class)
    void enqueuesOnlyUnindexedRestoresAfterCommit_AC_005_7(IndexStatus index) {
        deleted();
        ReflectionTestUtils.setField(document, "indexStatus", index);
        service.restore(user, id);
        verifyNoInteractions(jobs);
        if (index == IndexStatus.INDEXED) {
            assertThat(TransactionSynchronizationManager.getSynchronizations()).isEmpty();
        } else {
            assertThat(TransactionSynchronizationManager.getSynchronizations()).hasSize(1);
            commit();
            verify(jobs).enqueue("wiki-index", id);
        }
        assertThat(document.getIndexStatus()).isEqualTo(index);
    }

    @Test
    void resolvesAuditSpaceBeforeRemovingDocument_AC_005_8_AC_005_11_BUG_279() {
        deleted();
        doAnswer(call -> {
                    when(search.findDocumentSpace(id)).thenReturn(Optional.empty());
                    return null;
                })
                .when(documents)
                .flush();
        service.deletePermanently(user, id);
        verify(audit).record(AuditAction.DELETE, "WIKI_DOCUMENT", id, details("PERMANENT", "Guide"));
    }

    @Test
    void permanentlyDeletesAndCleansFileOnlyAfterCommit_AC_005_8_AC_005_11() {
        deleted();
        service.deletePermanently(user, id);
        verify(documents).delete(document);
        verify(documents).flush();
        verify(audit).record(AuditAction.DELETE, "WIKI_DOCUMENT", id, details("PERMANENT", "Guide"));
        verifyNoInteractions(storage, jobs);
        commit();
        verify(storage).delete("wiki/file");
    }

    @Test
    void logsFileKeyAtWarnWithoutFailingCommittedDelete_AC_005_12() {
        deleted();
        doThrow(new IllegalStateException("offline")).when(storage).delete("wiki/file");
        Logger logger = (Logger) LoggerFactory.getLogger(WikiTrashServiceImpl.class);
        var appender = new ListAppender<ILoggingEvent>();
        appender.start();
        logger.addAppender(appender);
        try {
            service.deletePermanently(user, id);
            commit();
            assertThat(appender.list).anySatisfy(event -> {
                assertThat(event.getLevel()).isEqualTo(Level.WARN);
                assertThat(event.getFormattedMessage()).contains("wiki/file");
            });
            verify(documents).delete(document);
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"TRASH", "RESTORE", "PERMANENT"})
    void rejectsWrongStateAndUnknownDocumentWithoutSideEffects_AC_005_9(String operation) {
        if (operation.equals("TRASH")) deleted();
        assertMissing(() -> act(operation));
        when(documents.findForIndexing(id)).thenReturn(Optional.empty());
        assertMissing(() -> act(operation));
        verify(documents, never()).saveAndFlush(any());
        verify(documents, never()).delete(any());
        verifyNoInteractions(audit, storage, jobs);
    }

    @ParameterizedTest
    @ValueSource(strings = {"TRASH", "RESTORE", "PERMANENT"})
    void returnsForbiddenForViewableDocumentAndNotFoundOtherwise_AC_005_10(String operation) {
        when(access.managesSpace(user, 42)).thenReturn(false);
        when(access.findAvailableDocuments(user, List.of(id))).thenReturn(Set.of(id));
        assertThatThrownBy(() -> act(operation)).isInstanceOf(AccessDeniedException.class);
        when(access.findAvailableDocuments(user, List.of(id))).thenReturn(Set.of());
        assertMissing(() -> act(operation));
        assertThat(document.getStatus()).isEqualTo(Status.PUBLISHED);
        verify(documents, never()).saveAndFlush(any());
        verify(documents, never()).delete(any());
        verifyNoInteractions(audit, storage, jobs);
    }

    @Test
    void mapsOnlyPublishedTitleConstraintAndSchedulesNothingOnConflict_AC_005_17_BUG_280() {
        deleted();
        var failure = new DataIntegrityViolationException(
                "duplicate", new ConstraintViolationException("duplicate", null, "uq_wiki_document_published_title"));
        when(documents.saveAndFlush(document)).thenThrow(failure);
        assertThatThrownBy(() -> service.restore(user, id))
                .isInstanceOfSatisfying(BusinessRuleException.class, error -> assertThat(error.getCode())
                        .isEqualTo("MSG84"));
        assertThat(TransactionSynchronizationManager.getSynchronizations()).isEmpty();
        verifyNoInteractions(audit, jobs, storage);
        var unrelated = new DataIntegrityViolationException("other constraint");
        doThrow(unrelated).when(documents).saveAndFlush(document);
        deleted();
        assertThatThrownBy(() -> service.restore(user, id)).isSameAs(unrelated);
    }

    @ParameterizedTest
    @ValueSource(strings = {"RESTORE", "PERMANENT"})
    void schedulesNoExternalSideEffectWhenAuditFails_AC_005_8_AC_005_11(String operation) {
        deleted();
        doThrow(new IllegalStateException("audit unavailable")).when(audit).record(any(), any(), any(), any());
        assertThatThrownBy(() -> act(operation)).isInstanceOf(IllegalStateException.class);
        assertThat(TransactionSynchronizationManager.getSynchronizations()).isEmpty();
        verifyNoInteractions(jobs, storage);
    }

    private Map<String, Object> details(String operation, String title) {
        return Map.of("operation", operation, "title", title, "spaceId", space);
    }

    private void deleted() {
        ReflectionTestUtils.setField(document, "status", Status.DELETED);
        ReflectionTestUtils.setField(document, "deletedBy", user);
        ReflectionTestUtils.setField(document, "deletedAt", Instant.EPOCH);
    }

    private void act(String operation) {
        switch (operation) {
            case "TRASH" -> service.trash(user, id);
            case "RESTORE" -> service.restore(user, id);
            case "PERMANENT" -> service.deletePermanently(user, id);
            default -> throw new IllegalArgumentException(operation);
        }
    }

    private void commit() {
        TransactionSynchronizationManager.getSynchronizations().forEach(sync -> sync.afterCommit());
    }

    private void assertMissing(org.assertj.core.api.ThrowableAssert.ThrowingCallable action) {
        assertThatThrownBy(action).isInstanceOfSatisfying(NotFoundException.class, error -> assertThat(error.getCode())
                .isEqualTo("MSG110"));
    }
}
