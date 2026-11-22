package vn.tsolve.wiki.service.impl;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import vn.tsolve.ai.client.EmbeddingClient;
import vn.tsolve.platform.service.FileStorage;
import vn.tsolve.wiki.entity.WikiDocument;
import vn.tsolve.wiki.entity.WikiDocument.IndexStatus;
import vn.tsolve.wiki.entity.WikiDocumentType;
import vn.tsolve.wiki.repository.WikiChunkRepository;
import vn.tsolve.wiki.repository.WikiDocumentRepository;
import vn.tsolve.wiki.service.DoclingClient;
import vn.tsolve.wiki.service.PassageSplitter;
import vn.tsolve.wiki.service.TypedChunker;
import vn.tsolve.worker.service.TenantJobHandler;

@Component
public class WikiIndexJobHandler implements TenantJobHandler {
    private static final Logger log = LoggerFactory.getLogger(WikiIndexJobHandler.class);
    private static final int BATCH_SIZE = 16;
    private final WikiDocumentRepository documents;
    private final WikiChunkRepository chunks;
    private final PassageSplitter splitter;
    private final TypedChunker typedChunker;
    private final DoclingClient docling;
    private final ObjectProvider<FileStorage> storage;
    private final EmbeddingClient embeddings;
    private final String model;
    private final TransactionTemplate read;
    private final TransactionTemplate write;
    private final TransactionTemplate outsideTransaction;

    public WikiIndexJobHandler(
            WikiDocumentRepository documents,
            WikiChunkRepository chunks,
            PassageSplitter splitter,
            TypedChunker typedChunker,
            DoclingClient docling,
            ObjectProvider<FileStorage> storage,
            EmbeddingClient embeddings,
            PlatformTransactionManager transactions,
            @Value("${OLLAMA_EMBED_MODEL:bge-m3}") String model) {
        this.documents = documents;
        this.chunks = chunks;
        this.splitter = splitter;
        this.typedChunker = typedChunker;
        this.docling = docling;
        this.storage = storage;
        this.embeddings = embeddings;
        this.model = model;
        read = new TransactionTemplate(transactions);
        read.setReadOnly(true);
        read.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        write = new TransactionTemplate(transactions);
        write.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        outsideTransaction = new TransactionTemplate(transactions);
        outsideTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_NOT_SUPPORTED);
    }

    @Override
    public String handlerType() {
        return "wiki-index";
    }

    @Override
    public void execute(UUID recordId) {
        outsideTransaction.executeWithoutResult(status -> index(recordId));
    }

    private void index(UUID id) {
        var document = read.execute(
                status -> documents.findById(id).filter(value -> value.getStatus() == WikiDocument.Status.PUBLISHED));
        if (document.isEmpty()) return;
        List<String> passages = passages(id, document.get());
        List<float[]> vectors;
        try {
            vectors = embed(passages);
        } catch (RuntimeException failure) {
            log.warn(
                    "Wiki indexing failed document={} cause={}",
                    id,
                    failure.getClass().getSimpleName());
            write.executeWithoutResult(status -> {
                if (isStillPublished(id)) documents.updateIndexStatus(id, IndexStatus.FAILED);
            });
            return;
        }
        write.executeWithoutResult(status -> {
            if (!isStillPublished(id)) return;
            chunks.deleteByDocumentId(id);
            for (int position = 0; position < passages.size(); position++) {
                chunks.insert(id, position, passages.get(position), Arrays.toString(vectors.get(position)), model);
            }
            documents.updateIndexStatus(id, IndexStatus.INDEXED);
        });
    }

    private List<String> passages(UUID id, WikiDocument document) {
        var type = document.getDocumentType();
        String title = document.getTitle();
        if (type != WikiDocumentType.OTHER) {
            try {
                String markdown = docling.convert(
                        () -> storage.getObject().read(document.getFileKey()), document.getContentType());
                var typed = typedChunker.split(type, title, markdown);
                if (typed.isEmpty()) throw new IllegalStateException("Docling produced no passages");
                return typed;
            } catch (RuntimeException failure) {
                log.warn(
                        "Wiki conversion fell back document={} cause={}",
                        id,
                        failure.getClass().getSimpleName());
            }
        }
        return splitter.split(type, title, document.getContent());
    }

    private List<float[]> embed(List<String> passages) {
        List<float[]> vectors = new ArrayList<>(passages.size());
        for (int start = 0; start < passages.size(); start += BATCH_SIZE) {
            List<String> batch = passages.subList(start, Math.min(start + BATCH_SIZE, passages.size()));
            List<float[]> result = embeddings.embed(batch);
            if (result == null || result.size() != batch.size()) {
                throw new IllegalStateException("Invalid embedding count");
            }
            for (float[] vector : result) {
                if (vector == null || vector.length != EmbeddingClient.DIMENSIONS) {
                    throw new IllegalStateException("Invalid embedding dimensions");
                }
                for (float value : vector) {
                    if (!Float.isFinite(value)) throw new IllegalStateException("Invalid embedding value");
                }
            }
            vectors.addAll(result);
        }
        return vectors;
    }

    private boolean isStillPublished(UUID id) {
        // Serialize replacement and recheck after the AI call, which must hold no database lock.
        return documents
                .findForIndexing(id)
                .filter(document -> document.getStatus() == WikiDocument.Status.PUBLISHED)
                .isPresent();
    }
}
