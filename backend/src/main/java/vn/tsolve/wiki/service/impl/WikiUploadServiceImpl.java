package vn.tsolve.wiki.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.hibernate.exception.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;
import vn.tsolve.common.audit.AuditAction;
import vn.tsolve.common.audit.AuditService;
import vn.tsolve.common.exception.BusinessRuleException;
import vn.tsolve.common.exception.FieldMessageCodeException;
import vn.tsolve.common.exception.NotFoundException;
import vn.tsolve.common.tenant.TenantContext;
import vn.tsolve.platform.service.FileStorage;
import vn.tsolve.wiki.dto.WikiDocumentFile;
import vn.tsolve.wiki.dto.WikiDocumentRef;
import vn.tsolve.wiki.dto.WikiPublishMetadata;
import vn.tsolve.wiki.dto.WikiUploadPreview;
import vn.tsolve.wiki.entity.WikiDocument;
import vn.tsolve.wiki.entity.WikiDocumentType;
import vn.tsolve.wiki.exception.WikiStorageUnavailableException;
import vn.tsolve.wiki.mapper.WikiDocumentMapper;
import vn.tsolve.wiki.repository.AccessRepository;
import vn.tsolve.wiki.repository.FolderRepository;
import vn.tsolve.wiki.repository.WikiDocumentRepository;
import vn.tsolve.wiki.repository.WikiSearchRepository;
import vn.tsolve.wiki.service.DocumentTypeClassifier;
import vn.tsolve.wiki.service.TextExtractor;
import vn.tsolve.wiki.service.WikiUploadService;
import vn.tsolve.worker.service.TenantJobService;
import vn.tsolve.worker.service.WebJobQueue;

@Service
public class WikiUploadServiceImpl implements WikiUploadService {
    private static final Logger log = LoggerFactory.getLogger(WikiUploadServiceImpl.class);
    private static final int MAX_FILE_BYTES = 10 * 1024 * 1024;
    private final WikiDocumentRepository documents;
    private final FolderRepository folders;
    private final AccessRepository access;
    private final WikiSearchRepository spaces;
    private final TextExtractor extractor;
    private final DocumentTypeClassifier classifier;
    // Optional like every FileStorage user while storage is not in every setup (StorageConfig, BUG-296).
    private final ObjectProvider<FileStorage> storage;
    private final AuditService audit;
    // Optional: the worker runs only with tsolve.worker.enabled (BUG-296).
    private final ObjectProvider<TenantJobService> jobs;
    // The web process has no worker; it queues the job for the worker process instead (BUG-360).
    private final ObjectProvider<WebJobQueue> webJobs;
    private final TransactionTemplate transaction;
    private final ObjectProvider<TenantContext> tenantContext;
    private final WikiDocumentMapper mapper;
    private final ObjectMapper json;

    public WikiUploadServiceImpl(
            WikiDocumentRepository documents,
            FolderRepository folders,
            AccessRepository access,
            WikiSearchRepository spaces,
            TextExtractor extractor,
            DocumentTypeClassifier classifier,
            ObjectProvider<FileStorage> storage,
            AuditService audit,
            ObjectProvider<TenantJobService> jobs,
            ObjectProvider<WebJobQueue> webJobs,
            PlatformTransactionManager transactions,
            ObjectProvider<TenantContext> tenantContext,
            WikiDocumentMapper mapper,
            ObjectMapper json) {
        this.documents = documents;
        this.folders = folders;
        this.access = access;
        this.spaces = spaces;
        this.extractor = extractor;
        this.classifier = classifier;
        this.storage = storage;
        this.audit = audit;
        this.jobs = jobs;
        this.webJobs = webJobs;
        this.transaction = new TransactionTemplate(transactions);
        this.transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.tenantContext = tenantContext;
        this.mapper = mapper;
        this.json = json;
    }

    @Override
    // No transaction: parsing up to 10 files must not hold a pooled connection (BUG-294).
    public List<WikiUploadPreview> extract(UUID userId, UUID spaceId, List<MultipartFile> files) {
        requireManager(userId, spaceId);
        // One file at a time: one extract holds at most one Ollama slot (AC-007.2, BUG-355),
        // and all of them share one time budget so the request ends before a proxy timeout (AC-007.3, BUG-360).
        var uploads = readFiles(files);
        var deadline = classifier.deadline();
        return uploads.stream()
                .map(file -> {
                    String name = file.name();
                    String title = name.substring(0, name.lastIndexOf('.')).strip();
                    String previewTitle = title.substring(0, Math.min(200, title.length()));
                    return new WikiUploadPreview(
                            name,
                            previewTitle,
                            file.text().substring(0, Math.min(300, file.text().length())),
                            file.words(),
                            file.text().isEmpty() ? "UNREADABLE" : "READY",
                            classifier.classify(previewTitle, file.text(), deadline));
                })
                .toList();
    }

    @Override
    public List<WikiDocumentRef> publish(UUID userId, UUID spaceId, List<MultipartFile> files, String metadataJson) {
        long internalId = requireManager(userId, spaceId);
        List<Upload> uploads = readFiles(files);
        WikiPublishMetadata metadata = parseMetadata(metadataJson);
        var documentTypes = validateMetadata(metadata, uploads.size());
        validateFolder(internalId, metadata.folderId());
        validateTitles(internalId, metadata);
        for (Upload upload : uploads) {
            if (upload.text().isEmpty()) {
                throw error("MSG90", Map.of("file_name", upload.name()), null);
            }
        }
        List<String> stored = new ArrayList<>();
        AtomicBoolean committed = new AtomicBoolean();
        try {
            for (Upload upload : uploads) {
                try (var input = new ByteArrayInputStream(upload.bytes())) {
                    stored.add(storage().store(input).key());
                }
            }
            return transaction.execute(status -> {
                long currentSpace =
                        access.lockActiveSpace(spaceId).orElseThrow(() -> new NotFoundException("MSG04", Map.of()));
                if (!access.managesSpace(userId, currentSpace)) throw new AccessDeniedException("MSG08");
                validateFolder(currentSpace, metadata.folderId());
                validateTitles(currentSpace, metadata);
                List<WikiDocumentRef> result = new ArrayList<>();
                for (int i = 0; i < uploads.size(); i++) {
                    Upload upload = uploads.get(i);
                    var row = metadata.documents().get(i);
                    WikiDocument document = new WikiDocument(
                            currentSpace,
                            metadata.folderId(),
                            row.title().strip(),
                            row.visibility(),
                            documentTypes.get(i),
                            upload.text(),
                            stored.get(i),
                            upload.name(),
                            upload.bytes().length,
                            upload.contentType(),
                            upload.words(),
                            userId,
                            Instant.now());
                    save(document, i);
                    record(document, spaceId);
                    result.add(mapper.toRef(document));
                }
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        // The rows are committed: an enqueue failure must not fail the upload (spec §35, BUG-295).
                        // The document stays PENDING and keyword-searchable (AC-001.11).
                        committed.set(true);
                        for (WikiDocumentRef document : result) {
                            try {
                                TenantJobService worker = jobs.getIfAvailable();
                                WebJobQueue web = webJobs.getIfAvailable();
                                if (worker != null) worker.enqueueFromRequest("wiki-index", document.documentId());
                                else if (web != null) web.enqueue("wiki-index", document.documentId());
                                else throw new IllegalStateException("No worker configured");
                            } catch (RuntimeException failure) {
                                // ponytail: no re-index sweep yet; PENDING documents need a manual re-enqueue.
                                log.error(
                                        "Could not enqueue wiki-index for document {}", document.documentId(), failure);
                            }
                        }
                    }
                });
                return List.copyOf(result);
            });
        } catch (IOException failure) {
            cleanup(stored);
            throw new UncheckedIOException(failure);
        } catch (RuntimeException failure) {
            if (!committed.get()) cleanup(stored);
            throw failure;
        }
    }

    @Override
    @Transactional(readOnly = true)
    public WikiDocumentFile download(UUID userId, UUID documentId) {
        if (!access.findAvailableDocuments(userId, List.of(documentId)).contains(documentId)) {
            throw new NotFoundException("MSG110", Map.of());
        }
        WikiDocument document =
                documents.findById(documentId).orElseThrow(() -> new NotFoundException("MSG110", Map.of()));
        String key = document.getFileKey();
        TenantContext context = tenantContext.getIfAvailable();
        UUID workspace = context == null ? null : context.requireWorkspaceId();
        return new WikiDocumentFile(
                document.getFileName(), document.getContentType(), document.getFileSize(), output -> {
                    if (context == null) transfer(key, output);
                    else {
                        try {
                            // MVC writes on another thread; restore the request's Workspace before FileStorage.read.
                            context.runIn(workspace, () -> {
                                try {
                                    transfer(key, output);
                                } catch (IOException failure) {
                                    throw new UncheckedIOException(failure);
                                }
                            });
                        } catch (UncheckedIOException failure) {
                            throw failure.getCause();
                        }
                    }
                });
    }

    private void transfer(String key, OutputStream output) throws IOException {
        try (var input = storage().read(key)) {
            input.transferTo(output);
        }
    }

    private FileStorage storage() {
        FileStorage target = storage.getIfAvailable();
        if (target == null) throw new WikiStorageUnavailableException();
        return target;
    }

    private long requireManager(UUID userId, UUID spaceId) {
        var space = spaces.findSpace(userId, spaceId).orElseThrow(() -> new NotFoundException("MSG04", Map.of()));
        if (!access.managesSpace(userId, space.spaceId())) throw new AccessDeniedException("MSG08");
        return space.spaceId();
    }

    private List<Upload> readFiles(List<MultipartFile> files) {
        if (files == null || files.isEmpty()) throw required("files");
        if (files.size() > 10) throw error("MSG88", Map.of(), null);
        List<Upload> uploads = new ArrayList<>();
        for (MultipartFile file : files) {
            if (file.getSize() > MAX_FILE_BYTES)
                throw new org.springframework.web.multipart.MaxUploadSizeExceededException(MAX_FILE_BYTES);
            String name = file.getOriginalFilename() == null ? "" : file.getOriginalFilename();
            byte[] bytes;
            try (var input = file.getInputStream()) {
                bytes = input.readNBytes(MAX_FILE_BYTES + 1);
            } catch (IOException unreadable) {
                String lower = name.toLowerCase(Locale.ROOT);
                if (!lower.endsWith(".pdf") && !lower.endsWith(".docx"))
                    throw error("MSG89", Map.of("file_name", name), null);
                uploads.add(new Upload(name, new byte[0], "", 0, ""));
                continue;
            }
            if (bytes.length > MAX_FILE_BYTES)
                throw new org.springframework.web.multipart.MaxUploadSizeExceededException(MAX_FILE_BYTES);
            String text = extractor.extract(bytes, name);
            int words = text.isEmpty() ? 0 : text.strip().split("(?U)\\s+").length;
            String contentType = name.toLowerCase(Locale.ROOT).endsWith(".pdf")
                    ? "application/pdf"
                    : "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
            uploads.add(new Upload(name, bytes, text, words, contentType));
        }
        return uploads;
    }

    private WikiPublishMetadata parseMetadata(String metadata) {
        if (metadata == null || metadata.isBlank()) throw required("metadata");
        try {
            return json.readValue(metadata, WikiPublishMetadata.class);
        } catch (JsonProcessingException invalid) {
            throw required("metadata");
        }
    }

    private List<WikiDocumentType> validateMetadata(WikiPublishMetadata metadata, int size) {
        if (metadata == null
                || metadata.documents() == null
                || metadata.documents().size() != size) throw required("metadata");
        var types = new ArrayList<WikiDocumentType>(size);
        for (int i = 0; i < size; i++) {
            var row = metadata.documents().get(i);
            if (row == null) throw required("metadata");
            String field = "documents[" + i + "].title";
            if (row.title() == null || row.title().isBlank()) throw required(field);
            if (row.title().length() > 200) throw error("MSG02", Map.of("field_name", field, "max_length", 200), field);
            if (row.visibility() == null) throw required("documents[" + i + "].visibility");
            String typeField = "documents[" + i + "].documentType";
            var type = row.documentType();
            if (type == null
                    || type.isNull()
                    || (type.isTextual() && type.textValue().isBlank())) {
                throw required(typeField);
            }
            if (!type.isTextual()) throw error("MSG06", Map.of(), typeField);
            try {
                types.add(WikiDocumentType.valueOf(type.textValue()));
            } catch (IllegalArgumentException invalid) {
                throw error("MSG06", Map.of(), typeField);
            }
        }
        return types;
    }

    private void validateFolder(long spaceId, UUID folderId) {
        if (folderId != null
                && folders.findById(folderId)
                        .filter(folder -> folder.getSpaceId() == spaceId)
                        .isEmpty()) {
            throw new NotFoundException("MSG04", Map.of());
        }
    }

    private void validateTitles(long spaceId, WikiPublishMetadata metadata) {
        var titles = new HashSet<String>();
        for (int i = 0; i < metadata.documents().size(); i++) {
            String title = metadata.documents().get(i).title().strip();
            if (!titles.add(title.toLowerCase(Locale.ROOT))
                    || documents.existsBySpaceIdAndFolderIdAndStatusAndTitleIgnoreCase(
                            spaceId, metadata.folderId(), WikiDocument.Status.PUBLISHED, title)) {
                throw duplicateTitle(i);
            }
        }
    }

    private void save(WikiDocument document, int index) {
        try {
            documents.saveAndFlush(document);
        } catch (DataIntegrityViolationException failure) {
            for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
                if (cause instanceof ConstraintViolationException constraint
                        && "uq_wiki_document_published_title".equals(constraint.getConstraintName()))
                    throw duplicateTitle(index);
            }
            throw failure;
        }
    }

    private BusinessRuleException duplicateTitle(int index) {
        return new BusinessRuleException("MSG84", Map.of(), "documents[" + index + "].title");
    }

    private void record(WikiDocument document, UUID spaceId) {
        Map<String, Object> details = new HashMap<>();
        details.put("operation", "UPLOAD");
        details.put("title", document.getTitle());
        details.put("spaceId", spaceId);
        details.put("folderId", document.getFolderId());
        details.put("visibility", document.getVisibility().name());
        details.put("documentType", document.getDocumentType().name());
        audit.record(AuditAction.PUBLISH, "WIKI_DOCUMENT", document.getId(), details);
    }

    private void cleanup(List<String> keys) {
        for (String key : keys) {
            try {
                storage().delete(key);
            } catch (RuntimeException failure) {
                log.warn("Could not remove failed Wiki upload key={}", key);
            }
        }
    }

    private FieldMessageCodeException required(String field) {
        return error("MSG01", Map.of("field_name", field), field);
    }

    private FieldMessageCodeException error(String code, Map<String, Object> params, String field) {
        return new FieldMessageCodeException(HttpStatus.BAD_REQUEST, code, params, field);
    }

    private record Upload(String name, byte[] bytes, String text, int words, String contentType) {}
}
