package vn.tsolve.wiki.service.impl;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.hibernate.exception.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import vn.tsolve.common.audit.AuditAction;
import vn.tsolve.common.audit.AuditService;
import vn.tsolve.common.exception.BusinessRuleException;
import vn.tsolve.common.exception.NotFoundException;
import vn.tsolve.platform.service.FileStorage;
import vn.tsolve.wiki.dto.WikiRestoreResult;
import vn.tsolve.wiki.dto.WikiTrashItem;
import vn.tsolve.wiki.entity.WikiDocument;
import vn.tsolve.wiki.repository.AccessRepository;
import vn.tsolve.wiki.repository.WikiDocumentRepository;
import vn.tsolve.wiki.repository.WikiSearchRepository;
import vn.tsolve.wiki.service.WikiTrashService;
import vn.tsolve.worker.service.TenantJobService;

@Service
@Transactional
public class WikiTrashServiceImpl implements WikiTrashService {
    private static final Logger log = LoggerFactory.getLogger(WikiTrashServiceImpl.class);
    private final WikiDocumentRepository documents;
    private final AccessRepository access;
    private final WikiSearchRepository search;
    private final FileStorage storage;
    private final AuditService audit;
    private final TenantJobService jobs;

    public WikiTrashServiceImpl(
            WikiDocumentRepository documents,
            AccessRepository access,
            WikiSearchRepository search,
            FileStorage storage,
            AuditService audit,
            TenantJobService jobs) {
        this.documents = documents;
        this.access = access;
        this.search = search;
        this.storage = storage;
        this.audit = audit;
        this.jobs = jobs;
    }

    @Override
    public void trash(UUID userId, UUID documentId) {
        WikiDocument document = requireManagedDocument(userId, documentId, WikiDocument.Status.PUBLISHED);
        document.trash(userId, Instant.now());
        documents.saveAndFlush(document);
        record(document, "TRASH", AuditAction.DELETE);
    }

    @Override
    @Transactional(readOnly = true)
    public List<WikiTrashItem> listTrash(UUID userId, UUID spaceId) {
        var space = search.findSpace(userId, spaceId).orElseThrow(() -> new NotFoundException("MSG04", Map.of()));
        if (!access.managesSpace(userId, space.spaceId())) throw new AccessDeniedException("MSG08");
        return search.listTrash(space.spaceId());
    }

    @Override
    public WikiRestoreResult restore(UUID userId, UUID documentId) {
        WikiDocument document = requireManagedDocument(userId, documentId, WikiDocument.Status.DELETED);
        String original = document.getTitle();
        String title = original;
        for (int number = 1;
                documents.existsBySpaceIdAndFolderIdAndStatusAndTitleIgnoreCase(
                        document.getSpaceId(), document.getFolderId(), WikiDocument.Status.PUBLISHED, title);
                number++) {
            String suffix = number == 1 ? " (restored)" : " (restored " + number + ")";
            title = original.substring(0, Math.min(original.length(), 200 - suffix.length())) + suffix;
        }
        document.restore(title, Instant.now());
        try {
            documents.saveAndFlush(document);
        } catch (DataIntegrityViolationException failure) {
            for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
                if (cause instanceof ConstraintViolationException constraint
                        && "uq_wiki_document_published_title".equals(constraint.getConstraintName())) {
                    throw new BusinessRuleException("MSG84", Map.of());
                }
            }
            throw failure;
        }
        record(document, "RESTORE", AuditAction.PUBLISH);
        if (document.getIndexStatus() != WikiDocument.IndexStatus.INDEXED) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    jobs.enqueue("wiki-index", documentId);
                }
            });
        }
        return new WikiRestoreResult(title, !title.equals(original));
    }

    @Override
    public void deletePermanently(UUID userId, UUID documentId) {
        WikiDocument document = requireManagedDocument(userId, documentId, WikiDocument.Status.DELETED);
        record(document, "PERMANENT", AuditAction.DELETE);
        documents.delete(document);
        documents.flush();
        String key = document.getFileKey();
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try {
                    storage.delete(key);
                } catch (RuntimeException failure) {
                    log.warn("Could not remove permanently deleted Wiki file key={}", key);
                }
            }
        });
    }

    private WikiDocument requireManagedDocument(UUID userId, UUID documentId, WikiDocument.Status status) {
        // Share the indexer's row lock so indexing and trash transitions cannot overwrite each other.
        WikiDocument document =
                documents.findForIndexing(documentId).orElseThrow(() -> new NotFoundException("MSG110", Map.of()));
        if (!access.managesSpace(userId, document.getSpaceId())) {
            if (access.findAvailableDocuments(userId, List.of(documentId)).contains(documentId)) {
                throw new AccessDeniedException("MSG08");
            }
            throw new NotFoundException("MSG110", Map.of());
        }
        if (document.getStatus() != status) throw new NotFoundException("MSG110", Map.of());
        return document;
    }

    private void record(WikiDocument document, String operation, AuditAction action) {
        UUID spaceId =
                search.findDocumentSpace(document.getId()).orElseThrow(() -> new NotFoundException("MSG110", Map.of()));
        audit.record(
                action,
                "WIKI_DOCUMENT",
                document.getId(),
                Map.of("operation", operation, "title", document.getTitle(), "spaceId", spaceId));
    }
}
