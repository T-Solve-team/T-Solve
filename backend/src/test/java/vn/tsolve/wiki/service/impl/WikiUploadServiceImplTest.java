package vn.tsolve.wiki.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.argThat;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.TextNode;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mapstruct.factory.Mappers;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;
import vn.tsolve.ai.client.LlmClient;
import vn.tsolve.common.audit.AuditAction;
import vn.tsolve.common.audit.AuditService;
import vn.tsolve.common.exception.CodedException;
import vn.tsolve.common.tenant.TenantContext;
import vn.tsolve.platform.service.FileStorage;
import vn.tsolve.wiki.InMemoryFileStorage;
import vn.tsolve.wiki.dto.WikiDocumentRef;
import vn.tsolve.wiki.dto.WikiPublishMetadata;
import vn.tsolve.wiki.dto.WikiUploadPreview;
import vn.tsolve.wiki.entity.WikiDocument;
import vn.tsolve.wiki.entity.WikiDocument.Visibility;
import vn.tsolve.wiki.entity.WikiDocumentType;
import vn.tsolve.wiki.exception.WikiStorageUnavailableException;
import vn.tsolve.wiki.mapper.WikiDocumentMapper;
import vn.tsolve.wiki.repository.AccessRepository;
import vn.tsolve.wiki.repository.FolderRepository;
import vn.tsolve.wiki.repository.WikiDocumentRepository;
import vn.tsolve.wiki.repository.WikiSearchRepository;
import vn.tsolve.wiki.service.DocumentTypeClassifier;
import vn.tsolve.wiki.service.TextExtractor;
import vn.tsolve.worker.service.TenantJobService;

class WikiUploadServiceImplTest {
    private final WikiDocumentRepository documents = mock(WikiDocumentRepository.class);
    private final FolderRepository folders = mock(FolderRepository.class);
    private final AccessRepository access = mock(AccessRepository.class);
    private final WikiSearchRepository spaces = mock(WikiSearchRepository.class);
    private final AuditService audit = mock(AuditService.class);
    private final TenantJobService jobs = mock(TenantJobService.class);
    private final InMemoryFileStorage storage = new InMemoryFileStorage();
    private final TestTransactions transactions = new TestTransactions();
    private final UUID user = UUID.randomUUID();
    private final UUID space = UUID.randomUUID();
    private final LlmClient llm = mock(LlmClient.class);
    private final DocumentTypeClassifier classifier = new DocumentTypeClassifier(llm, new ObjectMapper(), "3s", "60s");
    private final WikiUploadServiceImpl service = new WikiUploadServiceImpl(
            documents,
            folders,
            access,
            spaces,
            new TextExtractor(),
            classifier,
            provider(FileStorage.class, storage),
            audit,
            provider(TenantJobService.class, jobs),
            transactions,
            new StaticListableBeanFactory().getBeanProvider(TenantContext.class),
            Mappers.getMapper(WikiDocumentMapper.class),
            new ObjectMapper());

    @BeforeEach
    void setup() {
        when(spaces.findSpace(user, space)).thenReturn(Optional.of(new WikiSearchRepository.SpaceAccess(1L, true)));
        when(access.lockActiveSpace(space)).thenReturn(Optional.of(1L));
        when(access.managesSpace(user, 1L)).thenReturn(true);
        when(documents.saveAndFlush(any())).thenAnswer(call -> {
            WikiDocument document = call.getArgument(0);
            ReflectionTestUtils.setField(document, "id", UUID.randomUUID());
            return document;
        });
    }

    @Test
    void previewsInOrderWithTruncatedTitlesExcerptsAndNoWrites_AC_004_1_AC_004_2() throws IOException {
        List<MultipartFile> files = List.of(
                file("guide.pdf", "  " + "a".repeat(205) + ".PDF"),
                file("guide.docx", "doc.docx"),
                file("encrypted.pdf", "encrypted.pdf"),
                file("image-only.pdf", "scan.pdf"));
        when(llm.chat(any(), any())).thenReturn("{\"documentType\":\"GUIDE\"}");
        var result = service.extract(user, space, files);
        assertThat(result).hasSize(4);
        assertThat(result.getFirst().title()).isEqualTo("a".repeat(200));
        assertThat(result.getFirst().excerpt()).startsWith("Wiki guide").hasSizeLessThanOrEqualTo(300);
        assertThat(result.getFirst().wordCount()).isEqualTo(5);
        assertThat(result.get(1).fileName()).isEqualTo("doc.docx");
        assertThat(result.get(1).result()).isEqualTo("READY");
        assertThat(result.subList(0, 2))
                .allSatisfy(row -> assertThat(row.documentType()).isEqualTo(WikiDocumentType.GUIDE));
        assertThat(result.subList(2, 4)).allSatisfy(row -> {
            assertThat(row.result()).isEqualTo("UNREADABLE");
            assertThat(row.excerpt()).isEmpty();
            assertThat(row.wordCount()).isZero();
            assertThat(row.documentType()).isEqualTo(WikiDocumentType.OTHER);
        });
        assertThat(storage.files).isEmpty();
        verifyNoInteractions(documents, audit, jobs);
    }

    @Test
    void classifiesTheFilesOneAtATime_AC_007_2_BUG_355() throws IOException {
        var running = new AtomicInteger();
        var mostAtOnce = new AtomicInteger();
        when(llm.chat(any(), any())).thenAnswer(call -> {
            mostAtOnce.accumulateAndGet(running.incrementAndGet(), Math::max);
            Thread.sleep(50);
            running.decrementAndGet();
            return "{\"documentType\":\"GUIDE\"}";
        });
        var result = service.extract(
                user,
                space,
                List.of(file("guide.pdf", "one.pdf"), file("guide.pdf", "two.pdf"), file("guide.docx", "three.docx")));
        assertThat(result).extracting(WikiUploadPreview::documentType).containsOnly(WikiDocumentType.GUIDE);
        assertThat(mostAtOnce).hasValue(1);
    }

    @Test
    void rejectsMissingAndEleventhFileOnBothOperations_AC_004_3_AC_004_10() throws IOException {
        for (boolean publish : new boolean[] {false, true}) {
            assertCode(() -> invoke(publish, List.of(), metadata("Guide")), "MSG01", "files");
            var eleven = Collections.<MultipartFile>nCopies(11, file("guide.pdf", "guide.pdf"));
            assertCode(() -> invoke(publish, eleven, metadata("Guide")), "MSG88", null);
        }
        assertThat(storage.files).isEmpty();
    }

    @Test
    void rejectsWrongSignatureAndOversizeBeforeAnyStore_AC_004_4_AC_004_5_AC_004_10_AC_004_11() throws IOException {
        for (boolean publish : new boolean[] {false, true}) {
            assertCode(() -> invoke(publish, List.of(file("fake.pdf", "fake.pdf")), metadata("Guide")), "MSG89", null);
            var large = new MockMultipartFile("files", "large.pdf", "application/pdf", new byte[10 * 1024 * 1024 + 1]);
            assertThatThrownBy(() -> invoke(publish, List.of(large), metadata("Guide")))
                    .isInstanceOf(org.springframework.web.multipart.MaxUploadSizeExceededException.class);
        }
        assertThat(storage.files).isEmpty();
        verifyNoInteractions(documents, audit, jobs);
    }

    @Test
    void treatsMultipartReadFailureAsUnreadable_AC_004_2_AC_004_9_BUG_270() throws IOException {
        var broken = mock(MultipartFile.class);
        when(broken.getOriginalFilename()).thenReturn("broken.pdf");
        when(broken.getInputStream()).thenThrow(new IOException("Cannot read"));
        var result = service.extract(user, space, List.of(broken));
        assertThat(result.getFirst().result()).isEqualTo("UNREADABLE");
        assertThat(result.getFirst().excerpt()).isEmpty();
        assertThat(result.getFirst().wordCount()).isZero();
        assertCode(() -> publish(user, space, List.of(broken), metadata("Guide")), "MSG90", null);
        assertThat(storage.files).isEmpty();
    }

    @Test
    void acceptsTenFilesAtExactlyTenMegabytes_AC_004_1_AC_004_3_AC_004_5() {
        byte[] bytes = new byte[10 * 1024 * 1024];
        System.arraycopy("%PDF-".getBytes(), 0, bytes, 0, 5);
        TextExtractor parser = mock(TextExtractor.class);
        when(parser.extract(any(), any())).thenReturn("word ".repeat(100));
        var boundary = new WikiUploadServiceImpl(
                documents,
                folders,
                access,
                spaces,
                parser,
                classifier,
                provider(FileStorage.class, storage),
                audit,
                provider(TenantJobService.class, jobs),
                transactions,
                new StaticListableBeanFactory().getBeanProvider(TenantContext.class),
                Mappers.getMapper(WikiDocumentMapper.class),
                new ObjectMapper());
        var file = new MockMultipartFile("files", "large.pdf", "application/pdf", bytes);
        assertThat(boundary.extract(user, space, Collections.nCopies(10, file)))
                .hasSize(10)
                .allSatisfy(row -> {
                    assertThat(row.result()).isEqualTo("READY");
                    assertThat(row.excerpt()).isEqualTo("word ".repeat(60));
                    assertThat(row.wordCount()).isEqualTo(100);
                });
        assertThat(storage.files).isEmpty();
    }

    @Test
    void rejectsExistingTitleAndMapsOnlyTitleConstraintWithCleanup_AC_004_8_AC_004_11() throws IOException {
        when(documents.existsBySpaceIdAndFolderIdAndStatusAndTitleIgnoreCase(
                        1L, null, WikiDocument.Status.PUBLISHED, "Guide"))
                .thenReturn(true);
        assertCode(
                () -> publish(user, space, List.of(file("guide.pdf", "guide.pdf")), metadata("Guide")),
                "MSG84",
                "documents[0].title");
        assertThat(storage.files).isEmpty();
        when(documents.existsBySpaceIdAndFolderIdAndStatusAndTitleIgnoreCase(anyLong(), any(), any(), any()))
                .thenReturn(false);
        var constraint = new org.hibernate.exception.ConstraintViolationException(
                "duplicate", new java.sql.SQLException(), "uq_wiki_document_published_title");
        doThrow(new org.springframework.dao.DataIntegrityViolationException("duplicate", constraint))
                .when(documents)
                .saveAndFlush(any());
        assertCode(
                () -> publish(user, space, List.of(file("guide.pdf", "guide.pdf")), metadata("Guide")),
                "MSG84",
                "documents[0].title");
        assertThat(storage.files).isEmpty();
        verifyNoInteractions(audit, jobs);
    }

    @Test
    void publishesOriginalTextOwnerVisibilityAndPendingIndexWithAudit_AC_004_6_AC_004_12_AC_004_13()
            throws IOException {
        doAnswer(call -> {
                    assertThat(TransactionSynchronizationManager.isActualTransactionActive())
                            .isTrue();
                    return null;
                })
                .when(audit)
                .record(any(), any(), any(), any());
        doAnswer(call -> {
                    assertThat(transactions.committed).isTrue();
                    return UUID.randomUUID();
                })
                .when(jobs)
                .enqueueFromRequest(eq("wiki-index"), any());
        var result = publish(user, space, List.of(file("guide.pdf", "guide.pdf")), metadata("  Guide  "));
        assertThat(result).hasSize(1);
        assertThat(result.getFirst().title()).isEqualTo("Guide");
        var captor = org.mockito.ArgumentCaptor.forClass(WikiDocument.class);
        verify(documents).saveAndFlush(captor.capture());
        WikiDocument document = captor.getValue();
        assertThat(document.getOwnerId()).isEqualTo(user);
        assertThat(document.getContent()).contains("Wiki guide");
        assertThat(document.getWordCount()).isEqualTo(5);
        assertThat(document.getVisibility()).isEqualTo(Visibility.DEPARTMENT);
        assertThat(document.getStatus()).isEqualTo(WikiDocument.Status.PUBLISHED);
        assertThat(document.getIndexStatus()).isEqualTo(WikiDocument.IndexStatus.PENDING);
        assertThat(document.getFolderId()).isNull();
        assertThat(document.getContentType()).isEqualTo("application/pdf");
        assertThat(storage.files.get(document.getFileKey()))
                .isEqualTo(file("guide.pdf", "guide.pdf").getBytes());
        verify(audit)
                .record(
                        eq(AuditAction.PUBLISH),
                        eq("WIKI_DOCUMENT"),
                        eq(document.getId()),
                        argThat(details -> details.size() == 6
                                && details.get("operation").equals("UPLOAD")
                                && details.get("title").equals("Guide")
                                && details.get("spaceId").equals(space)
                                && details.containsKey("folderId")
                                && details.get("documentType").equals("GUIDE")
                                && details.get("visibility").equals("DEPARTMENT")));
        verify(jobs).enqueueFromRequest("wiki-index", document.getId());
    }

    @Test
    void anEnqueueFailureAfterCommitStillReturnsEveryPublishedDocument_BUG_295() throws IOException {
        when(jobs.enqueueFromRequest(eq("wiki-index"), any()))
                .thenThrow(new IllegalStateException("JobRunr down"))
                .thenReturn(UUID.randomUUID());
        var result = publish(
                user,
                space,
                List.of(file("guide.pdf", "a.pdf"), file("guide.pdf", "b.pdf")),
                new WikiPublishMetadata(null, List.of(row("A"), row("B"))));
        assertThat(result).extracting(WikiDocumentRef::title).containsExactly("A", "B");
        verify(jobs, org.mockito.Mockito.times(2)).enqueueFromRequest(eq("wiki-index"), any());
        assertThat(storage.files).hasSize(2);
    }

    @Test
    void extractHoldsNoTransactionWhileParsingFiles_BUG_294() throws NoSuchMethodException {
        assertThat(WikiUploadServiceImpl.class
                        .getMethod("extract", UUID.class, UUID.class, List.class)
                        .isAnnotationPresent(org.springframework.transaction.annotation.Transactional.class))
                .isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "\u2003"})
    void rejectsBlankTitles_AC_004_7(String title) throws IOException {
        assertCode(
                () -> publish(user, space, List.of(file("guide.pdf", "guide.pdf")), metadata(title)),
                "MSG01",
                "documents[0].title");
        assertThat(storage.files).isEmpty();
    }

    @Test
    void rejectsLongTitlesAndMissingOrMismatchedMetadata_AC_004_7_AC_004_10() throws IOException {
        var files = List.<MultipartFile>of(file("guide.pdf", "guide.pdf"));
        assertCode(() -> publish(user, space, files, metadata("a".repeat(201))), "MSG02", "documents[0].title");
        assertCode(() -> publish(user, space, files, null), "MSG01", "metadata");
        assertCode(() -> publish(user, space, files, new WikiPublishMetadata(null, List.of())), "MSG01", "metadata");
        assertThat(storage.files).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "",
                " ",
                "null",
                "{",
                "{}",
                "{\"documents\":[null]}",
                "{\"documents\":[{\"title\":\"Guide\",\"visibility\":\"PUBLIC\"}]}"
            })
    void rejectsInvalidJsonMetadataWithoutWrites_AC_004_10_AC_004_11_BUG_272(String metadata) throws IOException {
        assertCode(
                () -> service.publish(user, space, List.of(file("guide.pdf", "guide.pdf")), metadata),
                "MSG01",
                "metadata");
        assertThat(storage.files).isEmpty();
        verify(documents, never()).saveAndFlush(any());
        verifyNoInteractions(audit, jobs);
    }

    @ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({
        "'', MSG01", "' , \"documentType\": null', MSG01",
        "' , \"documentType\": \"\"', MSG01", "' , \"documentType\": \"guide\"', MSG06",
        "' , \"documentType\": 1', MSG06", "' , \"documentType\": {}', MSG06"
    })
    void rejectsMissingOrInvalidTypeOnItsOwnField_AC_004_10(String typeField, String code) throws IOException {
        List<MultipartFile> files = List.of(file("guide.pdf", "guide.pdf"), file("guide.pdf", "second.pdf"));
        String metadata = """
                {"documents":[{"title":"First","visibility":"DEPARTMENT","documentType":"POLICY"},
                              {"title":"Second","visibility":"DEPARTMENT"%s}]}
                """.formatted(typeField);
        assertCode(() -> service.publish(user, space, files, metadata), code, "documents[1].documentType");
        assertThat(storage.files).isEmpty();
        verifyNoInteractions(documents, audit, jobs);
    }

    @Test
    void rejectsDuplicateRequestTitlesAndUnreadablePublish_AC_004_8_AC_004_9_AC_004_11() throws IOException {
        assertCode(
                () -> publish(
                        user,
                        space,
                        List.of(file("guide.pdf", "first.pdf"), file("guide.docx", "second.docx")),
                        new WikiPublishMetadata(null, List.of(row("Guide"), row(" GUIDE ")))),
                "MSG84",
                "documents[1].title");
        assertCode(
                () -> publish(user, space, List.of(file("encrypted.pdf", "encrypted.pdf")), metadata("Secret")),
                "MSG90",
                null);
        assertThat(storage.files).isEmpty();
        verifyNoInteractions(audit, jobs);
    }

    @Test
    void removesStoredFilesOnStorageRowOrCommitFailureAndNeverQueues_AC_004_11_AC_004_13() throws IOException {
        storage.failOnStore = 2;
        assertThatThrownBy(() -> publish(
                        user,
                        space,
                        List.of(file("guide.pdf", "one.pdf"), file("guide.docx", "two.docx")),
                        new WikiPublishMetadata(null, List.of(row("One"), row("Two")))))
                .isInstanceOf(IllegalStateException.class);
        assertThat(storage.files).isEmpty();
        verify(documents, never()).saveAndFlush(any());
        verifyNoInteractions(audit, jobs);
        storage.failOnStore = Integer.MAX_VALUE;
        doThrow(new IllegalStateException("Database down")).when(documents).saveAndFlush(any());
        assertThatThrownBy(() -> publish(user, space, List.of(file("guide.pdf", "guide.pdf")), metadata("Guide")))
                .isInstanceOf(IllegalStateException.class);
        assertThat(storage.files).isEmpty();
        assertThat(transactions.rolledBack).isTrue();
        verifyNoInteractions(audit, jobs);
        reset(documents);
        when(documents.saveAndFlush(any())).thenAnswer(call -> {
            WikiDocument document = call.getArgument(0);
            ReflectionTestUtils.setField(document, "id", UUID.randomUUID());
            return document;
        });
        transactions.failCommit = true;
        assertThatThrownBy(() -> publish(user, space, List.of(file("guide.pdf", "guide.pdf")), metadata("Guide")))
                .isInstanceOf(IllegalStateException.class);
        assertThat(storage.files).isEmpty();
        verifyNoInteractions(jobs);
    }

    @Test
    void deniesOtherUsersBeforeReadingOrWritingFiles_AC_004_17() throws IOException {
        when(access.managesSpace(user, 1L)).thenReturn(false);
        var files = List.<MultipartFile>of(file("guide.pdf", "guide.pdf"));
        assertThatThrownBy(() -> service.extract(user, space, files)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> publish(user, space, files, metadata("Guide")))
                .isInstanceOf(AccessDeniedException.class);
        assertThat(storage.files).isEmpty();
        verifyNoInteractions(documents, audit, jobs);
    }

    @Test
    void streamsOnlyViewableOriginalAndClosesOnSuccessOrClientFailure_AC_002_12() throws IOException {
        var stored = storage.store(file("guide.pdf", "guide.pdf").getInputStream());
        UUID id = UUID.randomUUID();
        var document = new WikiDocument(
                1,
                null,
                "Guide",
                Visibility.DEPARTMENT,
                WikiDocumentType.OTHER,
                "text",
                stored.key(),
                "guide.pdf",
                stored.sizeBytes(),
                "application/pdf",
                1,
                user,
                Instant.now());
        when(access.findAvailableDocuments(user, List.of(id))).thenReturn(Set.of(id));
        when(documents.findById(id)).thenReturn(Optional.of(document));
        var download = service.download(user, id);
        var output = new ByteArrayOutputStream();
        download.content().writeTo(output);
        assertThat(output.toByteArray()).isEqualTo(storage.files.get(stored.key()));
        assertThat(download.fileName()).isEqualTo("guide.pdf");
        assertThat(storage.closedReads).isEqualTo(1);
        assertThatThrownBy(() -> download.content().writeTo(new java.io.OutputStream() {
                    @Override
                    public void write(int value) throws IOException {
                        throw new IOException("Disconnected");
                    }
                }))
                .isInstanceOf(IOException.class);
        assertThat(storage.closedReads).isEqualTo(2);
        when(access.findAvailableDocuments(user, List.of(id))).thenReturn(Set.of());
        assertCode(() -> service.download(user, id), "MSG110", null);
    }

    @Test
    void publishAnswers503WithMsg06WhenNoFileStorageIsConfigured_BUG_296() throws IOException {
        var unconfigured = new WikiUploadServiceImpl(
                documents,
                folders,
                access,
                spaces,
                new TextExtractor(),
                classifier,
                new StaticListableBeanFactory().getBeanProvider(FileStorage.class),
                audit,
                provider(TenantJobService.class, jobs),
                transactions,
                new StaticListableBeanFactory().getBeanProvider(TenantContext.class),
                Mappers.getMapper(WikiDocumentMapper.class),
                new ObjectMapper());
        assertThatThrownBy(() -> unconfigured.publish(
                        user,
                        space,
                        List.of(file("guide.pdf", "guide.pdf")),
                        new ObjectMapper().writeValueAsString(metadata("Guide"))))
                .isInstanceOfSatisfying(WikiStorageUnavailableException.class, error -> assertThat(error.getCode())
                        .isEqualTo("MSG06"));
        verifyNoInteractions(audit, jobs);
    }

    @Test
    void publishWithoutAWorkerStillReturnsThePendingDocuments_BUG_296() throws IOException {
        var noWorker = new WikiUploadServiceImpl(
                documents,
                folders,
                access,
                spaces,
                new TextExtractor(),
                classifier,
                provider(FileStorage.class, storage),
                audit,
                new StaticListableBeanFactory().getBeanProvider(TenantJobService.class),
                transactions,
                new StaticListableBeanFactory().getBeanProvider(TenantContext.class),
                Mappers.getMapper(WikiDocumentMapper.class),
                new ObjectMapper());
        var result = noWorker.publish(
                user,
                space,
                List.of(file("guide.pdf", "guide.pdf")),
                new ObjectMapper().writeValueAsString(metadata("Guide")));
        assertThat(result).extracting(WikiDocumentRef::title).containsExactly("Guide");
    }

    private static <T> org.springframework.beans.factory.ObjectProvider<T> provider(Class<T> type, T bean) {
        var factory = new StaticListableBeanFactory();
        factory.addBean("bean", bean);
        return factory.getBeanProvider(type);
    }

    private List<WikiDocumentRef> publish(
            UUID userId, UUID spaceId, List<MultipartFile> files, WikiPublishMetadata metadata) {
        try {
            return service.publish(
                    userId, spaceId, files, metadata == null ? null : new ObjectMapper().writeValueAsString(metadata));
        } catch (JsonProcessingException error) {
            throw new IllegalArgumentException(error);
        }
    }

    private void invoke(boolean publish, List<MultipartFile> files, WikiPublishMetadata metadata) {
        if (publish) publish(user, space, files, metadata);
        else service.extract(user, space, files);
    }

    private WikiPublishMetadata metadata(String title) {
        return new WikiPublishMetadata(null, List.of(row(title)));
    }

    private WikiPublishMetadata.Document row(String title) {
        return new WikiPublishMetadata.Document(title, Visibility.DEPARTMENT, TextNode.valueOf("GUIDE"));
    }

    private MockMultipartFile file(String fixture, String name) throws IOException {
        try (var input = getClass().getResourceAsStream("/wiki/" + fixture)) {
            return new MockMultipartFile("files", name, "application/octet-stream", input.readAllBytes());
        }
    }

    private void assertCode(org.assertj.core.api.ThrowableAssert.ThrowingCallable call, String code, String field) {
        assertThatThrownBy(call).isInstanceOfSatisfying(CodedException.class, error -> {
            assertThat(error.getCode()).isEqualTo(code);
            assertThat(error.getField()).isEqualTo(field);
        });
    }

    private static class TestTransactions extends AbstractPlatformTransactionManager {
        boolean committed;
        boolean rolledBack;
        boolean failCommit;

        @Override
        protected Object doGetTransaction() {
            return new Object();
        }

        @Override
        protected void doBegin(Object transaction, TransactionDefinition definition) {}

        @Override
        protected void doCommit(DefaultTransactionStatus status) {
            if (failCommit) throw new IllegalStateException("Commit failed");
            committed = true;
        }

        @Override
        protected void doRollback(DefaultTransactionStatus status) {
            rolledBack = true;
        }
    }
}
