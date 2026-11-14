package vn.tsolve.ticket.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import vn.tsolve.common.audit.AuditService;
import vn.tsolve.common.exception.InvalidFieldsException;
import vn.tsolve.common.exception.NotFoundException;
import vn.tsolve.common.exception.ProblemDetails.FieldError;
import vn.tsolve.platform.dto.StoredFile;
import vn.tsolve.platform.service.FileStorage;
import vn.tsolve.ticket.dto.EvidenceUpload;
import vn.tsolve.ticket.dto.ManualEvidence;
import vn.tsolve.ticket.dto.ManualTicketRequest;
import vn.tsolve.ticket.entity.ImportBatch;
import vn.tsolve.ticket.entity.ImportPreview;
import vn.tsolve.ticket.entity.ImportPreviewEvidence;
import vn.tsolve.ticket.entity.Ticket;
import vn.tsolve.ticket.entity.TicketEvidence;
import vn.tsolve.ticket.entity.TicketSource;
import vn.tsolve.ticket.exception.ProjectAccessDeniedException;
import vn.tsolve.ticket.exception.StorageUnavailableException;
import vn.tsolve.ticket.repository.ImportBatchRepository;
import vn.tsolve.ticket.repository.ImportPreviewEvidenceRepository;
import vn.tsolve.ticket.repository.ImportPreviewRepository;
import vn.tsolve.ticket.repository.ImportPreviewRowRepository;
import vn.tsolve.ticket.repository.TicketEvidenceRepository;
import vn.tsolve.ticket.repository.TicketRepository;
import vn.tsolve.ticket.repository.TicketReviewDecisionRepository;
import vn.tsolve.ticket.service.ProjectAccess;

/** Evidence lifecycle, including real Spring transaction synchronization without a database. */
class ManualTicketEvidenceTest {

    private static final long DRAFT = ManualTicketDraftTest.DRAFT;
    private static final long LIMIT = 10_485_760L;

    private final ImportPreviewRepository previews = mock(ImportPreviewRepository.class);
    private final ImportPreviewRowRepository rows = mock(ImportPreviewRowRepository.class);
    private final ImportPreviewEvidenceRepository draftEvidence = mock(ImportPreviewEvidenceRepository.class);
    private final TicketEvidenceRepository evidence = mock(TicketEvidenceRepository.class);
    private final TicketRepository tickets = mock(TicketRepository.class);
    private final ImportBatchRepository batches = mock(ImportBatchRepository.class);
    private final ProjectAccess projects = mock(ProjectAccess.class);
    private final FileStorage storage = mock(FileStorage.class);
    private final RecordingTransactionManager transactions = new RecordingTransactionManager();
    private final ObjectProvider<FileStorage> storageProvider = storageProvider();
    private final ManualTicketServiceImpl service = new ManualTicketServiceImpl(
            previews,
            rows,
            tickets,
            mock(TicketReviewDecisionRepository.class),
            batches,
            projects,
            mock(ApplicationEventPublisher.class),
            mock(AuditService.class),
            ManualTicketServiceImplTest.CLOCK,
            draftEvidence,
            evidence,
            storageProvider,
            transactions);

    @SuppressWarnings("unchecked")
    private static ObjectProvider<FileStorage> storageProvider() {
        return mock(ObjectProvider.class);
    }

    @BeforeEach
    void setUp() {
        when(storageProvider.getIfAvailable()).thenReturn(storage);
        when(projects.managesActive(ManualTicketServiceImplTest.PM.getName(), ManualTicketServiceImplTest.PROJECT))
                .thenReturn(true);
        when(batches.save(any())).thenAnswer(call -> {
            ImportBatch batch = call.getArgument(0);
            ReflectionTestUtils.setField(batch, "id", 7L);
            return batch;
        });
        when(tickets.saveAndFlush(any())).thenAnswer(call -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive())
                    .isTrue();
            Ticket ticket = call.getArgument(0);
            ReflectionTestUtils.setField(ticket, "id", ManualTicketServiceImplTest.TICKET);
            return ticket;
        });
        when(previews.save(any())).thenAnswer(call -> {
            ImportPreview draft = call.getArgument(0);
            ReflectionTestUtils.setField(draft, "id", DRAFT);
            return draft;
        });
        when(storage.store(any(InputStream.class))).thenAnswer(call -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive())
                    .isFalse();
            return new StoredFile("workspace/" + UUID.randomUUID(), 3);
        });
        doAnswer(call -> {
                    assertThat(TransactionSynchronizationManager.isActualTransactionActive())
                            .isFalse();
                    return null;
                })
                .when(storage)
                .delete(any());
    }

    private static EvidenceUpload file(String name, String type, long size) {
        return new EvidenceUpload(name, type, size, () -> new ByteArrayInputStream(new byte[] {1, 2, 3}));
    }

    private static ManualTicketRequest request(Long draftId, List<UUID> keep) {
        ManualTicketRequest v = ManualTicketServiceImplTest.valid();
        return new ManualTicketRequest(
                v.projectId(),
                v.ticketId(),
                v.title(),
                v.description(),
                v.resolvedDate(),
                v.symptoms(),
                v.rootCause(),
                v.resolutionSteps(),
                v.workaround(),
                v.scope(),
                draftId,
                keep);
    }

    private void save(boolean draft, ManualTicketRequest request, List<EvidenceUpload> files) {
        if (draft) {
            service.saveDraft(ManualTicketServiceImplTest.PM, request, files);
        } else {
            service.create(ManualTicketServiceImplTest.PM, request, files);
        }
    }

    private ImportPreview existingDraft(List<ImportPreviewEvidence> files) {
        ImportPreview draft = ManualTicketDraftTest.draft(ManualTicketDraftTest.OWNER, TicketSource.MANUAL);
        when(previews.findByIdAndOwner(DRAFT, ManualTicketDraftTest.OWNER)).thenReturn(Optional.of(draft));
        when(previews.lockByIdAndOwner(DRAFT, ManualTicketDraftTest.OWNER)).thenReturn(Optional.of(draft));
        when(rows.findById(any())).thenReturn(Optional.of(ManualTicketDraftTest.draftRow()));
        when(draftEvidence.findByPreviewIdOrderByCreatedAt(DRAFT)).thenReturn(files);
        return draft;
    }

    private static ImportPreviewEvidence kept(String name, String key) {
        ImportPreviewEvidence row = ImportPreviewEvidence.of(DRAFT, name, "text/plain", 3, key);
        ReflectionTestUtils.setField(row, "id", UUID.randomUUID());
        return row;
    }

    @Test
    void AC_006_15_savesKeptAndNewEvidenceAndNeverDeletesTheKeptObject() {
        ImportPreviewEvidence keep = kept("old.log", "workspace/kept");
        ImportPreviewEvidence drop = kept("drop.txt", "workspace/dropped");
        ImportPreview draft = existingDraft(List.of(keep, drop));
        when(storage.store(any())).thenAnswer(call -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive())
                    .isFalse();
            return new StoredFile("workspace/new", 3);
        });

        service.create(
                ManualTicketServiceImplTest.PM,
                request(DRAFT, List.of(keep.getId(), UUID.randomUUID())),
                List.of(file("NEW.PDF", "application/custom", 3)));

        ArgumentCaptor<TicketEvidence> saved = ArgumentCaptor.forClass(TicketEvidence.class);
        verify(evidence, org.mockito.Mockito.times(2)).save(saved.capture());
        assertThat(saved.getAllValues())
                .extracting(TicketEvidence::getTicketId)
                .containsOnly(ManualTicketServiceImplTest.TICKET);
        assertThat(saved.getAllValues())
                .extracting(TicketEvidence::getStorageKey)
                .containsExactly("workspace/kept", "workspace/new");
        assertThat(saved.getAllValues().get(0))
                .extracting(TicketEvidence::getFileName, TicketEvidence::getFileType, TicketEvidence::getFileSize)
                .containsExactly("old.log", "text/plain", 3L);
        assertThat(saved.getAllValues().get(1))
                .extracting(TicketEvidence::getFileName, TicketEvidence::getFileType, TicketEvidence::getFileSize)
                .containsExactly("NEW.PDF", "application/custom", 3L);
        verify(previews).delete(draft);
        verify(storage, never()).delete("workspace/kept");
        assertThat(transactions.committed).isTrue();
        verify(storage).delete("workspace/dropped");
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void AC_006_16_rejectsTheFirstBadFileBeforeStoringAnything(boolean draft) {
        for (EvidenceUpload bad : List.of(file("bad.exe", null, 3), file("large.png", "image/png", LIMIT + 1))) {
            InvalidFieldsException error = catchThrowableOfType(
                    () -> save(draft, request(null, null), List.of(file("good.txt", null, 3), bad)),
                    InvalidFieldsException.class);

            assertThat(error.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(error.getErrors())
                    .containsExactly(new FieldError("files", "MSG34", Map.of("file_name", bad.fileName())));
        }
        verifyNoInteractions(storageProvider, storage, evidence, draftEvidence);
        verify(previews, never()).save(any());
        verify(tickets, never()).saveAndFlush(any());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void AC_006_17_rejectsSixKeptAndNewFilesBeforeStoringAnything(boolean draft) {
        List<ImportPreviewEvidence> kept = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            kept.add(kept("old" + i + ".log", "workspace/" + i));
        }
        existingDraft(kept);

        InvalidFieldsException error = catchThrowableOfType(
                () -> save(
                        draft,
                        request(
                                DRAFT,
                                kept.stream().map(ImportPreviewEvidence::getId).toList()),
                        List.of(file("sixth.log", null, 3))),
                InvalidFieldsException.class);

        assertThat(error.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(error.getErrors()).containsExactly(new FieldError("files", "MSG72", Map.of()));
        verifyNoInteractions(storageProvider, storage);
        verify(previews, never()).delete(any());
    }

    @Test
    void AC_006_17_ignoresKeepIdsWithoutADraftAndAllowsFiveFiles() {
        List<EvidenceUpload> files = new ArrayList<>();
        for (String extension : List.of("PNG", "JpG", "JPEG", "DOCX", "XLSX")) {
            files.add(file("file." + extension, null, LIMIT));
        }

        service.create(ManualTicketServiceImplTest.PM, request(null, List.of(UUID.randomUUID())), files);

        verify(storage, org.mockito.Mockito.times(5)).store(any());
        ArgumentCaptor<TicketEvidence> saved = ArgumentCaptor.forClass(TicketEvidence.class);
        verify(evidence, org.mockito.Mockito.times(5)).save(saved.capture());
        assertThat(saved.getAllValues())
                .allSatisfy(row -> assertThat(row.getFileType()).isNotBlank());
        verifyNoInteractions(draftEvidence);
    }

    @Test
    void AC_006_18_savesDraftRowsAndReopensTheirIdsNamesAndSizes() {
        List<ImportPreviewEvidence> savedRows = new ArrayList<>();
        when(draftEvidence.save(any())).thenAnswer(call -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive())
                    .isTrue();
            ImportPreviewEvidence row = call.getArgument(0);
            ReflectionTestUtils.setField(row, "id", UUID.randomUUID());
            savedRows.add(row);
            return row;
        });

        service.saveDraft(
                ManualTicketServiceImplTest.PM,
                request(null, null),
                List.of(file("error.LOG", " ", 3), file("notes.txt", null, 3)));
        existingDraft(savedRows);

        assertThat(service.getDraft(ManualTicketServiceImplTest.PM, DRAFT).evidence())
                .containsExactlyElementsOf(savedRows.stream()
                        .map(row -> new ManualEvidence(row.getId(), row.getFileName(), row.getFileSize()))
                        .toList());
        assertThat(savedRows).allSatisfy(row -> {
            assertThat(row.getPreviewId()).isEqualTo(DRAFT);
            assertThat(row.getFileType()).isEqualTo("text/plain");
            assertThat(row.getStorageKey()).startsWith("workspace/");
        });
    }

    @Test
    void AC_006_19_deletesDroppedRowsInTheTransactionAndObjectsAfterCommit() {
        ImportPreviewEvidence keep = kept("keep.txt", "workspace/keep");
        ImportPreviewEvidence drop = kept("drop.txt", "workspace/drop");
        existingDraft(List.of(keep, drop));
        doAnswer(call -> {
                    assertThat(TransactionSynchronizationManager.isActualTransactionActive())
                            .isTrue();
                    assertThat(transactions.committed).isFalse();
                    return null;
                })
                .when(draftEvidence)
                .delete(drop);
        doAnswer(call -> {
                    assertThat(transactions.committed).isTrue();
                    assertThat(TransactionSynchronizationManager.isActualTransactionActive())
                            .isFalse();
                    throw new IllegalStateException("delete unavailable");
                })
                .when(storage)
                .delete("workspace/drop");

        service.saveDraft(
                ManualTicketServiceImplTest.PM, request(DRAFT, List.of(keep.getId(), UUID.randomUUID())), List.of());

        verify(draftEvidence).delete(drop);
        verify(draftEvidence, never()).delete(keep);
        verify(storage).delete("workspace/drop");
        verify(storage, never()).delete("workspace/keep");
        verify(storage, never()).store(any());
    }

    @Test
    void AC_006_19_deletingAManualDraftDeletesObjectsAfterCommit() {
        ImportPreview draft = existingDraft(List.of(kept("old.txt", "workspace/old")));
        ImportDraftServiceImpl drafts = draftService();
        doAnswer(call -> {
                    assertThat(transactions.committed).isTrue();
                    assertThat(TransactionSynchronizationManager.isActualTransactionActive())
                            .isFalse();
                    return null;
                })
                .when(storage)
                .delete("workspace/old");

        drafts.delete(ManualTicketDraftTest.OWNER, DRAFT);

        verify(previews).delete(draft);
        verify(storage).delete("workspace/old");
    }

    private ImportDraftServiceImpl draftService() {
        return new ImportDraftServiceImpl(
                previews,
                rows,
                projects,
                ManualTicketServiceImplTest.CLOCK,
                draftEvidence,
                storageProvider,
                transactions);
    }

    @Test
    void AC_006_19_draftDeleteSkipsUnavailableStorageAndIgnoresDeleteFailures() {
        existingDraft(List.of(kept("old.txt", "workspace/old")));
        when(storageProvider.getIfAvailable()).thenReturn(null);
        draftService().delete(ManualTicketDraftTest.OWNER, DRAFT);
        verifyNoInteractions(storage);

        when(storageProvider.getIfAvailable()).thenReturn(storage);
        doThrow(new IllegalStateException("unavailable")).when(storage).delete("workspace/old");
        draftService().delete(ManualTicketDraftTest.OWNER, DRAFT);
        verify(storage).delete("workspace/old");
    }

    @Test
    void AC_006_20_draftDeleteFailureKeepsItsObjects() {
        existingDraft(List.of(kept("old.txt", "workspace/old")));
        transactions.failCommit = true;

        assertThatThrownBy(() -> draftService().delete(ManualTicketDraftTest.OWNER, DRAFT))
                .isInstanceOf(IllegalStateException.class);

        verifyNoInteractions(storageProvider, storage);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void AC_006_20_commitFailureDeletesNewObjectsAndKeepsOldObjects(boolean draft) {
        ImportPreviewEvidence old = kept("old.log", "workspace/old");
        existingDraft(List.of(old));
        when(storage.store(any())).thenReturn(new StoredFile("workspace/new", 3));
        transactions.failCommit = true;

        assertThatThrownBy(() -> save(draft, request(DRAFT, null), List.of(file("new.log", null, 3))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("commit failed");

        verify(storage).delete("workspace/new");
        verify(storage, never()).delete("workspace/old");
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void AC_006_20_secondStoreFailureDeletesTheFirstObjectAndSavesNothing(boolean draft) {
        when(storage.store(any()))
                .thenReturn(new StoredFile("workspace/first", 3))
                .thenThrow(new IllegalStateException("No Workspace is bound"));

        StorageUnavailableException error = catchThrowableOfType(
                () -> save(
                        draft, request(null, null), List.of(file("first.log", null, 3), file("second.log", null, 3))),
                StorageUnavailableException.class);

        assertThat(error.getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(error.getCode()).isEqualTo("MSG06");
        verify(storage).delete("workspace/first");
        verifyNoInteractions(evidence, draftEvidence, batches, rows);
        verify(previews, never()).save(any());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void AC_006_21_missingStorageWithFilesIs503AndWithoutFilesWorks(boolean draft) {
        when(storageProvider.getIfAvailable()).thenReturn(null);

        StorageUnavailableException error = catchThrowableOfType(
                () -> save(draft, request(null, null), List.of(file("file.log", null, 3))),
                StorageUnavailableException.class);

        assertThat(error.getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(error.getCode()).isEqualTo("MSG06");
        assertThat(transactions.committed).isFalse();
        org.mockito.Mockito.clearInvocations(storageProvider);
        save(draft, request(null, null), List.of());
        assertThat(transactions.committed).isTrue();
        verifyNoInteractions(storageProvider, storage);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void AC_006_16_existingFieldProjectAndDraftChecksRunBeforeFileChecks(boolean draft) {
        List<EvidenceUpload> invalid = List.of(file("bad.exe", null, 3));
        ManualTicketRequest noProject =
                new ManualTicketRequest(null, null, null, null, null, null, null, null, null, null, null, null);
        InvalidFieldsException error =
                catchThrowableOfType(() -> save(draft, noProject, invalid), InvalidFieldsException.class);
        assertThat(error.getErrors().get(0).field()).isEqualTo("projectId");

        when(projects.managesActive(ManualTicketDraftTest.OWNER, ManualTicketServiceImplTest.PROJECT))
                .thenReturn(false);
        assertThatThrownBy(() -> save(draft, request(null, null), invalid))
                .isInstanceOf(ProjectAccessDeniedException.class);
        when(projects.managesActive(ManualTicketDraftTest.OWNER, ManualTicketServiceImplTest.PROJECT))
                .thenReturn(true);
        assertThatThrownBy(() -> save(draft, request(DRAFT, null), invalid)).isInstanceOf(NotFoundException.class);
        verifyNoInteractions(storageProvider, storage);
    }

    private static class RecordingTransactionManager extends AbstractPlatformTransactionManager {

        boolean committed;
        boolean failCommit;

        @Override
        protected Object doGetTransaction() {
            return new Object();
        }

        @Override
        protected void doBegin(Object transaction, TransactionDefinition definition) {}

        @Override
        protected void doCommit(DefaultTransactionStatus status) {
            if (failCommit) {
                throw new IllegalStateException("commit failed");
            }
            committed = true;
        }

        @Override
        protected void doRollback(DefaultTransactionStatus status) {}
    }
}
