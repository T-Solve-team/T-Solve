package vn.tsolve.wiki.service.impl;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import vn.tsolve.common.audit.AuditAction;
import vn.tsolve.common.audit.AuditService;
import vn.tsolve.common.exception.BusinessRuleException;
import vn.tsolve.common.exception.NotFoundException;
import vn.tsolve.wiki.dto.CreateWikiFolderRequest;
import vn.tsolve.wiki.dto.UpdateWikiFolderRequest;
import vn.tsolve.wiki.dto.WikiFolder;
import vn.tsolve.wiki.entity.Folder;
import vn.tsolve.wiki.exception.WikiFolderInvalidParentException;
import vn.tsolve.wiki.mapper.WikiFolderMapper;
import vn.tsolve.wiki.repository.AccessRepository;
import vn.tsolve.wiki.repository.FolderRepository;
import vn.tsolve.wiki.repository.WikiDocumentRepository;
import vn.tsolve.wiki.service.WikiFolderService;

@Service
@Transactional
public class WikiFolderServiceImpl implements WikiFolderService {
    private final FolderRepository folders;
    private final WikiDocumentRepository documents;
    private final AccessRepository access;
    private final AuditService audit;
    private final WikiFolderMapper mapper;
    private final TransactionTemplate transaction;

    public WikiFolderServiceImpl(
            FolderRepository folders,
            WikiDocumentRepository documents,
            AccessRepository access,
            AuditService audit,
            WikiFolderMapper mapper,
            PlatformTransactionManager transactionManager) {
        this.folders = folders;
        this.documents = documents;
        this.access = access;
        this.audit = audit;
        this.mapper = mapper;
        this.transaction = new TransactionTemplate(transactionManager);
    }

    @Override
    public WikiFolder create(UUID userId, UUID spaceId, CreateWikiFolderRequest request) {
        long internalId = requireManager(userId, spaceId);
        int level = parentLevel(internalId, request.parentId()) + 1;
        if (level > 3) throw new WikiFolderInvalidParentException();
        Folder folder = new Folder(
                internalId, request.parentId(), request.name().strip(), (short) level, userId, Instant.now());
        save(folder);
        record("CREATE", folder, spaceId);
        return mapper.toResponse(folder);
    }

    @Override
    public WikiFolder update(UUID userId, UUID folderId, UpdateWikiFolderRequest request) {
        UUID spaceId = findSpace(folderId);
        long internalId = requireManager(userId, spaceId);
        Folder folder = findFolder(folderId);
        List<Folder> subtree = folders.findSubtree(folderId);
        if (request.parentId() != null
                && subtree.stream().anyMatch(child -> child.getId().equals(request.parentId()))) {
            throw new WikiFolderInvalidParentException();
        }
        int level = parentLevel(internalId, request.parentId()) + 1;
        int delta = level - folder.getLevel();
        int deepest = subtree.stream().mapToInt(Folder::getLevel).max().orElse(folder.getLevel());
        if (deepest + delta > 3) throw new WikiFolderInvalidParentException();
        Instant now = Instant.now();
        for (Folder child : subtree) {
            if (!child.getId().equals(folderId) && delta != 0)
                child.changeLevel((short) (child.getLevel() + delta), now);
        }
        folder.edit(request.name().strip(), request.parentId(), (short) level, now);
        save(folder); // Flushes the managed descendants in this transaction too.
        record("UPDATE", folder, spaceId);
        return mapper.toResponse(folder);
    }

    @Override
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void delete(UUID userId, UUID folderId) {
        try {
            transaction.executeWithoutResult(status -> deleteFolder(userId, folderId));
        } catch (DataIntegrityViolationException failure) {
            for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
                if (cause instanceof ConstraintViolationException constraint
                        && ("wiki_document_folder_id_fkey".equals(constraint.getConstraintName())
                                || "folder_parent_id_fkey".equals(constraint.getConstraintName()))) {
                    // PostgreSQL aborts the failed transaction; recount only after its rollback.
                    throw transaction.execute(status -> new BusinessRuleException(
                            "MSG87",
                            Map.of(
                                    "folder_count",
                                    folders.countByParentId(folderId),
                                    "document_count",
                                    documents.countByFolderId(folderId))));
                }
            }
            throw failure;
        }
    }

    private void deleteFolder(UUID userId, UUID folderId) {
        UUID spaceId = findSpace(folderId);
        requireManager(userId, spaceId);
        Folder folder = findFolder(folderId);
        long folderCount = folders.countByParentId(folderId);
        long documentCount = documents.countByFolderId(folderId);
        if (folderCount > 0 || documentCount > 0) {
            throw new BusinessRuleException(
                    "MSG87", Map.of("folder_count", folderCount, "document_count", documentCount));
        }
        folders.delete(folder);
        folders.flush();
        record("DELETE", folder, spaceId);
    }

    private long requireManager(UUID userId, UUID spaceId) {
        long internalId = access.findActiveSpace(spaceId).orElseThrow(() -> new NotFoundException("MSG04", Map.of()));
        if (!access.managesSpace(userId, internalId)) throw new AccessDeniedException("MSG08");
        // Serialize this Space's tree mutations only after checking management access.
        return access.lockActiveSpace(spaceId).orElseThrow(() -> new NotFoundException("MSG04", Map.of()));
    }

    private UUID findSpace(UUID folderId) {
        return access.findFolderSpace(folderId).orElseThrow(() -> new NotFoundException("MSG04", Map.of()));
    }

    private Folder findFolder(UUID folderId) {
        return folders.findById(folderId).orElseThrow(() -> new NotFoundException("MSG04", Map.of()));
    }

    private int parentLevel(long spaceId, UUID parentId) {
        if (parentId == null) return 0;
        Folder parent = findFolder(parentId);
        if (parent.getSpaceId() != spaceId) throw new NotFoundException("MSG04", Map.of());
        return parent.getLevel();
    }

    private void save(Folder folder) {
        try {
            folders.saveAndFlush(folder);
        } catch (DataIntegrityViolationException failure) {
            for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
                if (cause instanceof ConstraintViolationException constraint
                        && "uq_folder_parent_name".equals(constraint.getConstraintName())) {
                    throw new BusinessRuleException("MSG85", Map.of(), "name");
                }
            }
            throw failure;
        }
    }

    private void record(String operation, Folder folder, UUID spaceId) {
        Map<String, Object> details = new HashMap<>();
        details.put("operation", operation);
        details.put("name", folder.getName());
        details.put("spaceId", spaceId);
        details.put("parentId", folder.getParentId());
        audit.record(AuditAction.FOLDER_CHANGE, "WIKI_FOLDER", folder.getId(), details);
    }
}
