package vn.tsolve.wiki.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import vn.tsolve.ai.client.EmbeddingClient;
import vn.tsolve.ai.exception.AiUnavailableException;
import vn.tsolve.wiki.entity.WikiDocument;
import vn.tsolve.wiki.entity.WikiDocument.IndexStatus;
import vn.tsolve.wiki.entity.WikiDocument.Status;
import vn.tsolve.wiki.repository.WikiChunkRepository;
import vn.tsolve.wiki.repository.WikiDocumentRepository;
import vn.tsolve.wiki.service.PassageSplitter;

@ExtendWith(OutputCaptureExtension.class)
class WikiIndexJobHandlerTest {
    private final WikiDocumentRepository documents = mock(WikiDocumentRepository.class);
    private final WikiChunkRepository chunks = mock(WikiChunkRepository.class);
    private final EmbeddingClient embeddings = mock(EmbeddingClient.class);
    private final Transactions transactions = new Transactions();
    private final UUID id = UUID.randomUUID();
    private WikiIndexJobHandler handler;

    @BeforeEach
    void setUp() {
        handler = new WikiIndexJobHandler(documents, chunks, new PassageSplitter(), embeddings, transactions, "bge-m3");
    }

    @Test
    void registersTheUploadJobType_AC_004_14() {
        assertThat(handler.handlerType()).isEqualTo("wiki-index");
    }

    @Test
    void embedsFirstTwoHundredPassagesInBatchesOfSixteenBeforeWriting_AC_004_14() {
        List<String> passages = IntStream.range(0, 201)
                .mapToObj(i -> "passage-%03d ".formatted(i) + "x".repeat(980))
                .toList();
        published(String.join("\n\n", passages));
        AtomicInteger embedded = new AtomicInteger();
        List<Integer> batchSizes = new ArrayList<>();
        when(embeddings.embed(anyList())).thenAnswer(call -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive())
                    .isFalse();
            List<String> batch = call.getArgument(0);
            batchSizes.add(batch.size());
            assertThat(batch)
                    .containsExactlyElementsOf(passages.subList(embedded.get(), embedded.get() + batch.size()));
            return batch.stream()
                    .map(text -> vector(embedded.getAndIncrement() + 1))
                    .toList();
        });
        doAnswer(call -> {
                    assertThat(embedded).hasValue(200);
                    assertWriteTransaction();
                    return null;
                })
                .when(chunks)
                .deleteByDocumentId(id);
        doAnswer(call -> {
                    assertWriteTransaction();
                    return null;
                })
                .when(chunks)
                .insert(any(), anyInt(), any(), any(), any());

        handler.execute(id);

        assertThat(batchSizes).containsExactly(16, 16, 16, 16, 16, 16, 16, 16, 16, 16, 16, 16, 8);
        for (int i = 0; i < 200; i++) {
            verify(chunks).insert(id, i, passages.get(i), Arrays.toString(vector(i + 1)), "bge-m3");
        }
        var order = org.mockito.Mockito.inOrder(chunks, documents);
        order.verify(chunks).deleteByDocumentId(id);
        order.verify(chunks, times(200)).insert(eq(id), anyInt(), any(), any(), eq("bge-m3"));
        order.verify(documents).updateIndexStatus(id, IndexStatus.INDEXED);
        assertThat(transactions.writesCommitted).isEqualTo(1);
    }

    @Test
    void repeatedDeliveryReplacesChunks_AC_004_14() {
        published("Guide");
        when(embeddings.embed(List.of("Guide"))).thenReturn(List.of(vector(1)));
        handler.execute(id);
        handler.execute(id);
        verify(chunks, times(2)).deleteByDocumentId(id);
        verify(chunks, times(2)).insert(id, 0, "Guide", Arrays.toString(vector(1)), "bge-m3");
        verify(documents, times(2)).updateIndexStatus(id, IndexStatus.INDEXED);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void embeddingFailureKeepsNoNewPassagesAndRecordsFailure_AC_004_15(boolean late, CapturedOutput output) {
        published(("private document " + "x".repeat(980) + "\n\n").repeat(17));
        RuntimeException failure =
                late ? new IllegalStateException("private exception text") : new AiUnavailableException();
        when(embeddings.embed(anyList()))
                .thenAnswer(call -> {
                    if (late) {
                        List<String> batch = call.getArgument(0);
                        return batch.stream().map(text -> vector(1)).toList();
                    }
                    throw failure;
                })
                .thenThrow(failure);

        handler.execute(id);

        verifyNoInteractions(chunks);
        verify(documents).updateIndexStatus(id, IndexStatus.FAILED);
        assertThat(transactions.writesCommitted).isEqualTo(1);
        assertThat(output).doesNotContain("private document", "private exception text");
    }

    @ParameterizedTest
    @ValueSource(strings = {"count", "dimensions", "nonfinite", "null"})
    void unusableEmbeddingIsAFailedRun_AC_004_15(String problem) {
        published("Guide");
        float[] vector = vector(1);
        if (problem.equals("nonfinite")) vector[0] = Float.NaN;
        when(embeddings.embed(anyList()))
                .thenReturn(
                        switch (problem) {
                            case "count" -> List.of();
                            case "dimensions" -> List.of(new float[3]);
                            case "null" -> null;
                            default -> List.of(vector);
                        });
        handler.execute(id);
        verifyNoInteractions(chunks);
        verify(documents).updateIndexStatus(id, IndexStatus.FAILED);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void missingOrTrashedDocumentChangesNothing_AC_004_16(boolean missing) {
        WikiDocument document = document("Guide");
        ReflectionTestUtils.setField(document, "status", Status.DELETED);
        when(documents.findById(id)).thenReturn(missing ? Optional.empty() : Optional.of(document));
        handler.execute(id);
        verify(documents).findById(id);
        verifyNoInteractions(embeddings, chunks);
        assertThat(transactions.writesCommitted).isZero();
        verify(documents, times(0)).updateIndexStatus(any(), any());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void documentTrashedDuringEmbeddingIsUnchanged_AC_004_16(boolean embeddingFails) {
        published("Guide");
        WikiDocument deleted = document("Guide");
        ReflectionTestUtils.setField(deleted, "status", Status.DELETED);
        doReturn(Optional.of(deleted)).when(documents).findForIndexing(id);
        when(embeddings.embed(anyList())).thenAnswer(call -> {
            if (embeddingFails) throw new AiUnavailableException();
            return List.of(vector(1));
        });
        handler.execute(id);
        verifyNoInteractions(chunks);
        verify(documents, times(0)).updateIndexStatus(any(), any());
    }

    @Test
    void storageFailureRollsBackAndPropagatesToTheWorker_AC_004_14() {
        published("Guide");
        when(embeddings.embed(anyList())).thenReturn(List.of(vector(1)));
        doThrow(new IllegalStateException("storage unavailable"))
                .when(chunks)
                .insert(any(), anyInt(), any(), any(), any());
        assertThatThrownBy(() -> handler.execute(id)).isInstanceOf(IllegalStateException.class);
        assertThat(transactions.writesCommitted).isZero();
        assertThat(transactions.rollbacks).isEqualTo(1);
        verify(documents, times(0)).updateIndexStatus(any(), any());
    }

    private void published(String content) {
        WikiDocument document = document(content);
        when(documents.findById(id)).thenAnswer(call -> {
            assertThat(TransactionSynchronizationManager.isCurrentTransactionReadOnly())
                    .isTrue();
            return Optional.of(document);
        });
        when(documents.findForIndexing(id)).thenAnswer(call -> {
            assertWriteTransaction();
            return Optional.of(document);
        });
    }

    private WikiDocument document(String content) {
        return new WikiDocument(
                1L,
                null,
                "Guide",
                WikiDocument.Visibility.COMPANY_WIDE,
                content,
                "wiki/file",
                "guide.pdf",
                42,
                "application/pdf",
                1,
                UUID.randomUUID(),
                Instant.now());
    }

    private static float[] vector(int value) {
        float[] vector = new float[EmbeddingClient.DIMENSIONS];
        vector[0] = value;
        return vector;
    }

    private static void assertWriteTransaction() {
        assertThat(TransactionSynchronizationManager.isActualTransactionActive())
                .isTrue();
        assertThat(TransactionSynchronizationManager.isCurrentTransactionReadOnly())
                .isFalse();
    }

    private static class Transactions extends AbstractPlatformTransactionManager {
        int writesCommitted;
        int rollbacks;

        @Override
        protected Object doGetTransaction() {
            return new Object();
        }

        @Override
        protected void doBegin(Object transaction, TransactionDefinition definition) {}

        @Override
        protected void doCommit(DefaultTransactionStatus status) {
            if (!status.isReadOnly()) writesCommitted++;
        }

        @Override
        protected void doRollback(DefaultTransactionStatus status) {
            rollbacks++;
        }
    }
}
