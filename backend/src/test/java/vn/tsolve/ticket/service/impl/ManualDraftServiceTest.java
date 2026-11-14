package vn.tsolve.ticket.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.web.multipart.MultipartFile;
import vn.tsolve.common.audit.AuditService;
import vn.tsolve.common.exception.MessageCodeException;
import vn.tsolve.common.exception.NotFoundException;
import vn.tsolve.common.security.Role;
import vn.tsolve.common.security.TSolveUser;
import vn.tsolve.ticket.dto.DraftSavedResponse;
import vn.tsolve.ticket.dto.ManualDraftResponse;
import vn.tsolve.ticket.entity.DraftEvidence;
import vn.tsolve.ticket.entity.ImportBatch;
import vn.tsolve.ticket.entity.ImportPreview;
import vn.tsolve.ticket.entity.ImportPreviewRow;
import vn.tsolve.ticket.entity.PreviewRowStatus;
import vn.tsolve.ticket.entity.Ticket;
import vn.tsolve.ticket.entity.TicketEvidence;
import vn.tsolve.ticket.entity.TicketReviewDecision;
import vn.tsolve.ticket.entity.TicketSource;
import vn.tsolve.ticket.repository.ImportBatchRepository;
import vn.tsolve.ticket.repository.ImportPreviewRepository;
import vn.tsolve.ticket.repository.ImportPreviewRowRepository;
import vn.tsolve.ticket.repository.TicketEvidenceRepository;
import vn.tsolve.ticket.repository.TicketRepository;
import vn.tsolve.ticket.repository.TicketReviewDecisionRepository;
import vn.tsolve.ticket.service.ProjectAccess;

/** Drafts of a manual entry (STORY-007): save, update, reopen, and Save as Ticket from a Draft. */
class ManualDraftServiceTest {

    private static final UUID PROJECT = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID OTHER_PROJECT = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID PM_ID = UUID.fromString("00000000-0000-0000-0000-0000000000aa");
    private static final String OWNER = PM_ID.toString();
    private static final UUID TICKET = UUID.fromString("00000000-0000-0000-0000-000000000101");
    private static final UUID E1 = UUID.fromString("00000000-0000-0000-0000-0000000000e1");
    private static final UUID E2 = UUID.fromString("00000000-0000-0000-0000-0000000000e2");
    private static final Instant NOW = Instant.parse("2026-11-14T03:00:00Z");
    private static final Instant EARLIER = NOW.minusSeconds(86_400);
    private static final TSolveUser PM =
            new TSolveUser(PM_ID, "pm@company.vn", "Nguyen Van An", Set.of(Role.PROJECT_MANAGER));
    private static final String FULL = "\"ticketId\":\"T-1\",\"title\":\"Login fails\",\"description\":\"Users cannot"
            + " sign in\",\"symptoms\":\"Error 500\",\"rootCause\":\"Bad token\",\"resolutionSteps\":\"Restart\","
            + "\"scope\":\"All users\"";

    private final ManualEntryFileStorage storage = new ManualEntryFileStorage();
    private final ProjectAccess projects = mock(ProjectAccess.class);
    private final TicketRepository tickets = mock(TicketRepository.class);
    private final TicketEvidenceRepository evidenceRows = mock(TicketEvidenceRepository.class);
    private final TicketReviewDecisionRepository decisions = mock(TicketReviewDecisionRepository.class);
    private final ImportBatchRepository batches = mock(ImportBatchRepository.class);
    private final ImportBatchRecorder recorder = mock(ImportBatchRecorder.class);
    private final ImportPreviewRepository previews = mock(ImportPreviewRepository.class);
    private final ImportPreviewRowRepository previewRows = mock(ImportPreviewRowRepository.class);
    private final List<String> transactions = new ArrayList<>();

    private final PlatformTransactionManager txManager = new PlatformTransactionManager() {
        @Override
        public TransactionStatus getTransaction(TransactionDefinition definition) {
            return new SimpleTransactionStatus();
        }

        @Override
        public void commit(TransactionStatus status) {
            transactions.add("commit");
        }

        @Override
        public void rollback(TransactionStatus status) {
            transactions.add("rollback");
        }
    };

    private final ManualEntryServiceImpl service = new ManualEntryServiceImpl(
            new ObjectMapper(),
            new ManualEntryValidator(Clock.fixed(NOW, ZoneOffset.UTC)),
            new EvidenceFiles(Optional.of(storage)),
            projects,
            tickets,
            evidenceRows,
            decisions,
            batches,
            recorder,
            mock(ApplicationEventPublisher.class),
            txManager,
            mock(AuditService.class),
            previews,
            previewRows,
            Clock.fixed(NOW, ZoneOffset.UTC));

    @BeforeEach
    void setUp() {
        when(projects.managesActive(OWNER, PROJECT)).thenReturn(true);
        when(projects.managesActive(OWNER, OTHER_PROJECT)).thenReturn(true);
        when(previews.findByIdAndOwner(anyLong(), anyString())).thenReturn(Optional.empty());
        when(previews.lockByIdAndOwner(anyLong(), anyString())).thenReturn(Optional.empty());
        when(previews.save(any(ImportPreview.class))).thenAnswer(call -> {
            ImportPreview preview = call.getArgument(0);
            ReflectionTestUtils.setField(preview, "id", 42L);
            return preview;
        });
        when(previewRows.save(any(ImportPreviewRow.class))).thenAnswer(call -> call.getArgument(0));
        when(tickets.findExistingTicketIds(anyCollection())).thenReturn(Set.of());
        when(batches.save(any(ImportBatch.class))).thenAnswer(call -> {
            ImportBatch batch = call.getArgument(0);
            ReflectionTestUtils.setField(batch, "id", 7L);
            return batch;
        });
        when(tickets.save(any(Ticket.class))).thenAnswer(call -> {
            Ticket ticket = call.getArgument(0);
            ReflectionTestUtils.setField(ticket, "id", TICKET);
            return ticket;
        });
    }

    private static MockMultipartFile file(String name, String content) {
        return new MockMultipartFile(
                "files", name, "application/octet-stream", content.getBytes(StandardCharsets.UTF_8));
    }

    private static String entry(String fields) {
        return "{\"projectId\":\"" + PROJECT + "\"" + (fields.isEmpty() ? "" : "," + fields) + "}";
    }

    /** PM's Manual Draft 42 with the files e1 (key k1) and e2 (key k2), both in the storage. */
    private ImportPreview draft() {
        storage.objects.put("k1", new byte[] {1});
        storage.objects.put("k2", new byte[] {2});
        ImportPreview preview = ImportPreview.manualDraft(OWNER, PROJECT, EARLIER);
        ReflectionTestUtils.setField(preview, "id", 42L);
        preview.replaceEvidence(List.of(
                new DraftEvidence(E1, "k1", "a.png", "png", 1), new DraftEvidence(E2, "k2", "b.log", "log", 1)));
        ImportPreviewRow row = ImportPreviewRow.manualEntry(
                42L,
                new ImportPreviewRow.Values(
                        "T-1", "Old title", null, null, "2026-11-01", null, null, null, null, null));
        when(previews.findByIdAndOwner(42L, OWNER)).thenReturn(Optional.of(preview));
        when(previews.lockByIdAndOwner(42L, OWNER)).thenReturn(Optional.of(preview));
        when(previewRows.findById(new ImportPreviewRow.Id(42L, 1))).thenReturn(Optional.of(row));
        return preview;
    }

    private MessageCodeException draftRejection(String entry, List<MultipartFile> files) {
        return catchThrowableOfType(() -> service.saveDraft(PM, entry, files), MessageCodeException.class);
    }

    @Test
    void aNewDraftKeepsEveryFieldAsEnteredAndItsFilesAndAnswersMsg41_AC_007_1() {
        DraftSavedResponse response = service.saveDraft(
                PM, entry(FULL + ",\"workaround\":\"Clear cache\""), List.of(file("shot.jpeg", "abc")));

        assertThat(response.previewId()).isEqualTo(42L);
        assertThat(response.savedAt()).isEqualTo(NOW);
        assertThat(response.message().code()).isEqualTo("MSG41");

        ArgumentCaptor<ImportPreview> preview = ArgumentCaptor.forClass(ImportPreview.class);
        verify(previews).save(preview.capture());
        assertThat(preview.getValue().getSource()).isEqualTo(TicketSource.MANUAL);
        assertThat(preview.getValue().getOwner()).isEqualTo(OWNER);
        assertThat(preview.getValue().getProjectId()).isEqualTo(PROJECT);
        assertThat(preview.getValue().getDraftSavedAt()).isEqualTo(NOW);
        assertThat(preview.getValue().getEvidence()).singleElement().satisfies(file -> {
            assertThat(file.fileName()).isEqualTo("shot.jpeg");
            assertThat(file.fileType()).isEqualTo("jpg");
            assertThat(file.fileSize()).isEqualTo(3);
            assertThat(storage.objects).containsKey(file.storageKey());
        });

        ArgumentCaptor<ImportPreviewRow> row = ArgumentCaptor.forClass(ImportPreviewRow.class);
        verify(previewRows).save(row.capture());
        assertThat(row.getValue().getId()).isEqualTo(new ImportPreviewRow.Id(42L, 1));
        assertThat(row.getValue().getStatus()).isEqualTo(PreviewRowStatus.VALID);
        assertThat(row.getValue().getValues())
                .isEqualTo(new ImportPreviewRow.Values(
                        "T-1",
                        "Login fails",
                        "Users cannot sign in",
                        null,
                        null,
                        "Error 500",
                        "Bad token",
                        "Restart",
                        "Clear cache",
                        "All users"));
        // a Draft is not an import: no Ticket, no batch
        verifyNoInteractions(tickets, batches, recorder);
    }

    @Test
    void aDraftChecksNothingButTheProjectAndKeepsAResolvedDateThatIsNoDateAsText_AC_007_1() {
        // edge 25: "2026-13-40" stays as entered; a blank Ticket ID and a 300-character title are kept too
        service.saveDraft(
                PM,
                entry("\"ticketId\":\"  \",\"title\":\"" + "x".repeat(300) + "\",\"resolvedDate\":\"2026-13-40\""),
                List.of());

        ArgumentCaptor<ImportPreviewRow> row = ArgumentCaptor.forClass(ImportPreviewRow.class);
        verify(previewRows).save(row.capture());
        assertThat(row.getValue().getValues().ticketId()).isEqualTo("  ");
        assertThat(row.getValue().getValues().title()).hasSize(300);
        assertThat(row.getValue().getValues().resolvedDate()).isEqualTo("2026-13-40");
    }

    @Test
    void aDraftWithoutAProjectIsMsg01AndSavesNothing_AC_007_2() {
        MessageCodeException e = draftRejection("{\"title\":\"x\"}", List.of(file("a.png", "1")));

        assertThat(e.getStatus().value()).isEqualTo(400);
        assertThat(e.getCode()).isEqualTo("MSG01");
        assertThat(e.getField()).isEqualTo("projectId");
        assertThat(storage.objects).isEmpty();
        verify(previews, never()).save(any());
    }

    @Test
    void aDraftOfAProjectTheCallerDoesNotManageIs403AndSavesNothing_AC_007_2() {
        when(projects.managesActive(OWNER, PROJECT)).thenReturn(false);

        MessageCodeException e = draftRejection(entry(""), List.of(file("a.png", "1")));

        assertThat(e.getStatus().value()).isEqualTo(403);
        assertThat(storage.objects).isEmpty();
        verify(previews, never()).save(any());
    }

    @Test
    void aDraftWithAWrongFileIsMsg34AndWithSixFilesMsg72_AC_007_2() {
        assertThat(draftRejection(entry(""), List.of(file("a.zip", "1"))).getCode())
                .isEqualTo("MSG34");
        List<MultipartFile> six = new ArrayList<>();
        for (int i = 1; i <= 6; i++) {
            six.add(file(i + ".png", "1"));
        }
        assertThat(draftRejection(entry(""), six).getCode()).isEqualTo("MSG72");
        assertThat(storage.objects).isEmpty();
        verify(previews, never()).save(any());
    }

    @Test
    void aReopenedDraftReturnsItsFieldsAndItsFilesWithoutTheirKeys_AC_007_4() {
        draft();

        ManualDraftResponse response = service.getDraft(OWNER, 42L);

        assertThat(response.previewId()).isEqualTo(42L);
        assertThat(response.projectId()).isEqualTo(PROJECT);
        assertThat(response.ticketId()).isEqualTo("T-1");
        assertThat(response.title()).isEqualTo("Old title");
        assertThat(response.resolvedDate()).isEqualTo("2026-11-01");
        assertThat(response.description()).isNull();
        assertThat(response.savedAt()).isEqualTo(EARLIER);
        assertThat(response.evidence())
                .extracting(f -> f.id() + " " + f.fileName() + " " + f.fileType() + " " + f.fileSize())
                .containsExactly(E1 + " a.png png 1", E2 + " b.log log 1");
    }

    @Test
    void anotherUsersAMissingOrAJiraDraftIs404Msg04_AC_007_8() {
        draft();
        ImportPreview jira = ImportPreview.jira(OWNER, PROJECT, null, null, false, NOW);
        jira.saveAsDraft(NOW);
        ReflectionTestUtils.setField(jira, "id", 7L);
        when(previews.findByIdAndOwner(7L, OWNER)).thenReturn(Optional.of(jira));
        when(previews.lockByIdAndOwner(7L, OWNER)).thenReturn(Optional.of(jira));

        for (Runnable call : List.<Runnable>of(
                () -> service.getDraft("someone-else", 42L),
                () -> service.getDraft(OWNER, 99L),
                () -> service.getDraft(OWNER, 7L),
                () -> service.saveDraft(PM, entry("\"draftId\":99"), List.of(file("a.png", "1"))),
                () -> service.saveDraft(PM, entry("\"draftId\":7"), List.of(file("a.png", "1"))),
                () -> service.save(PM, entry(FULL + ",\"draftId\":7"), List.of(file("a.png", "1"))))) {
            assertThat(catchThrowableOfType(call::run, NotFoundException.class).getCode())
                    .isEqualTo("MSG04");
        }
        // the Draft is looked up before any file is stored
        assertThat(storage.objects).containsOnlyKeys("k1", "k2");
        verify(tickets, never()).save(any());
    }

    @Test
    void savingAReopenedDraftUpdatesItAddsNewFilesAndDeletesTheRemovedOnes_AC_007_5() {
        ImportPreview preview = draft();

        DraftSavedResponse response = service.saveDraft(
                PM,
                "{\"projectId\":\"" + OTHER_PROJECT + "\",\"draftId\":42,\"keepEvidenceIds\":[\"" + E1 + "\"],"
                        + "\"title\":\"New title\"}",
                List.of(file("c.pdf", "xyz")));

        assertThat(response.previewId()).isEqualTo(42L);
        assertThat(preview.getDraftSavedAt()).isEqualTo(NOW);
        assertThat(preview.getProjectId()).isEqualTo(OTHER_PROJECT);
        assertThat(preview.getEvidence()).extracting(DraftEvidence::fileName).containsExactly("a.png", "c.pdf");
        assertThat(previewRows
                        .findById(new ImportPreviewRow.Id(42L, 1))
                        .orElseThrow()
                        .getValues())
                .satisfies(values -> {
                    assertThat(values.title()).isEqualTo("New title");
                    // every field is replaced by the form, so a cleared field is cleared in the Draft
                    assertThat(values.ticketId()).isNull();
                    assertThat(values.resolvedDate()).isNull();
                });
        // the same Draft, no second one; the removed file e2 leaves the storage after the commit
        verify(previews, never()).save(any());
        assertThat(storage.deleted).containsExactly("k2");
        assertThat(storage.objects).containsKey("k1").doesNotContainKey("k2").hasSize(2);
        assertThat(transactions).containsExactly("commit");
    }

    @Test
    void aDraftSavedWithoutKeepEvidenceIdsKeepsEveryFileAndAnEmptyListRemovesThemAll_AC_007_5() {
        ImportPreview preview = draft();

        service.saveDraft(PM, entry("\"draftId\":42"), List.of());
        assertThat(preview.getEvidence()).extracting(DraftEvidence::id).containsExactly(E1, E2);
        assertThat(storage.deleted).isEmpty();

        service.saveDraft(PM, entry("\"draftId\":42,\"keepEvidenceIds\":[]"), List.of());
        assertThat(preview.getEvidence()).isEmpty();
        assertThat(storage.deleted).containsExactlyInAnyOrder("k1", "k2");
    }

    @Test
    void keptAndNewFilesTogetherAreAtMostFive_AC_007_5() {
        ImportPreview preview = draft();
        List<MultipartFile> four = new ArrayList<>();
        for (int i = 1; i <= 4; i++) {
            four.add(file(i + ".png", "1"));
        }

        MessageCodeException e = draftRejection(entry("\"draftId\":42"), four);

        assertThat(e.getCode()).isEqualTo("MSG72");
        assertThat(storage.objects).containsOnlyKeys("k1", "k2");
        assertThat(preview.getEvidence()).hasSize(2);
        assertThat(transactions).isEmpty();
    }

    @Test
    void filesAnotherRequestAddedBeforeTheLockCountTowardsFive_AC_006_12_BUG_215() {
        ImportPreview preview = draft();
        // another request added 2 files between the first read (2 files) and the lock: 4 + 2 new is over five
        ImportPreview changed = ImportPreview.manualDraft(OWNER, PROJECT, EARLIER);
        ReflectionTestUtils.setField(changed, "id", 42L);
        changed.replaceEvidence(List.of(
                preview.getEvidence().get(0),
                preview.getEvidence().get(1),
                new DraftEvidence(UUID.randomUUID(), "k3", "c.png", "png", 1),
                new DraftEvidence(UUID.randomUUID(), "k4", "d.png", "png", 1)));
        when(previews.lockByIdAndOwner(42L, OWNER)).thenReturn(Optional.of(changed));
        List<MultipartFile> two = List.of(file("e.png", "1"), file("f.png", "1"));

        for (Runnable call : List.<Runnable>of(
                () -> service.saveDraft(PM, entry("\"draftId\":42"), two),
                () -> service.save(PM, entry(FULL + ",\"draftId\":42"), two))) {
            assertThat(catchThrowableOfType(call::run, MessageCodeException.class)
                            .getCode())
                    .isEqualTo("MSG72");
        }
        assertThat(changed.getEvidence()).hasSize(4);
        assertThat(storage.objects).containsOnlyKeys("k1", "k2");
        assertThat(transactions).containsExactly("rollback", "rollback");
        verify(tickets, never()).save(any());
        verifyNoInteractions(recorder);
    }

    @Test
    void aKeptFileTheLockedDraftNoLongerHasIs404AndTheNewFilesAreDeleted_AC_007_9() {
        ImportPreview preview = draft();
        // edge 28: another request removed e1 between the first read and the lock
        ImportPreview changed = ImportPreview.manualDraft(OWNER, PROJECT, EARLIER);
        ReflectionTestUtils.setField(changed, "id", 42L);
        changed.replaceEvidence(List.of(preview.getEvidence().get(1)));
        when(previews.lockByIdAndOwner(42L, OWNER)).thenReturn(Optional.of(changed));
        String keepE1 = "\"draftId\":42,\"keepEvidenceIds\":[\"" + E1 + "\"]";

        for (Runnable call : List.<Runnable>of(
                () -> service.saveDraft(PM, entry(keepE1), List.of(file("c.pdf", "1"))),
                () -> service.save(PM, entry(FULL + "," + keepE1), List.of(file("c.pdf", "1"))))) {
            assertThat(catchThrowableOfType(call::run, NotFoundException.class).getCode())
                    .isEqualTo("MSG04");
        }
        assertThat(changed.getEvidence()).extracting(DraftEvidence::id).containsExactly(E2);
        assertThat(storage.objects).containsOnlyKeys("k1", "k2");
        assertThat(transactions).containsExactly("rollback", "rollback");
        verify(tickets, never()).save(any());
        verifyNoInteractions(recorder);
    }

    @Test
    void aDraftDeletedByAnotherTabIs404WhenSavedAgain_AC_007_8() {
        // edge 23: the Draft was found first, then saved as a Ticket in another tab before the lock
        draft();
        when(previews.lockByIdAndOwner(42L, OWNER)).thenReturn(Optional.empty());

        assertThat(catchThrowableOfType(
                                () -> service.saveDraft(PM, entry("\"draftId\":42"), List.of(file("c.pdf", "1"))),
                                NotFoundException.class)
                        .getCode())
                .isEqualTo("MSG04");
        assertThat(storage.objects).containsOnlyKeys("k1", "k2");
    }

    @Test
    void savingADraftAsATicketMovesTheKeptFilesToItAndDeletesTheDraft_AC_007_6() {
        ImportPreview preview = draft();

        service.save(
                PM,
                entry(FULL + ",\"draftId\":42,\"keepEvidenceIds\":[\"" + E1 + "\"]"),
                List.of(file("c.pdf", "xyz")));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<TicketEvidence>> rows = ArgumentCaptor.forClass(List.class);
        verify(evidenceRows).saveAll(rows.capture());
        assertThat(rows.getValue())
                .extracting(r -> r.getTicketId() + " " + r.getFileName() + " " + r.getFileType())
                .containsExactly(TICKET + " a.png png", TICKET + " c.pdf pdf");
        assertThat(rows.getValue().get(0).getStorageKey()).isEqualTo("k1");
        verify(previews).delete(preview);
        assertThat(storage.deleted).containsExactly("k2");
        assertThat(storage.objects).containsKey("k1").doesNotContainKey("k2");
        assertThat(transactions).containsExactly("commit");
    }

    @Test
    void aRejectedOrFailedSaveLeavesTheDraftAsItWas_AC_007_6() {
        ImportPreview preview = draft();
        String fromDraft = entry(FULL + ",\"draftId\":42,\"keepEvidenceIds\":[\"" + E1 + "\"]");

        // MSG31 before the transaction
        when(tickets.findExistingTicketIds(anyCollection())).thenReturn(Set.of("T-1"));
        assertThat(catchThrowableOfType(
                                () -> service.save(PM, fromDraft, List.of(file("c.pdf", "1"))),
                                MessageCodeException.class)
                        .getCode())
                .isEqualTo("MSG31");

        // MSG33 inside it
        when(tickets.findExistingTicketIds(anyCollection())).thenReturn(Set.of());
        when(decisions.save(any(TicketReviewDecision.class))).thenThrow(new QueryTimeoutException("db"));
        assertThat(catchThrowableOfType(
                                () -> service.save(PM, fromDraft, List.of(file("c.pdf", "1"))),
                                MessageCodeException.class)
                        .getCode())
                .isEqualTo("MSG33");

        verify(previews, never()).delete(any());
        assertThat(preview.getEvidence()).extracting(DraftEvidence::id).containsExactly(E1, E2);
        // the Draft's files stay; only the new ones of each request are gone
        assertThat(storage.objects).containsOnlyKeys("k1", "k2");
        assertThat(transactions).containsExactly("rollback");
    }
}
