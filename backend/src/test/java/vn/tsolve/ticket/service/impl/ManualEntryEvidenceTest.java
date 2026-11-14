package vn.tsolve.ticket.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.web.multipart.MultipartFile;
import vn.tsolve.common.audit.AuditService;
import vn.tsolve.common.exception.MessageCodeException;
import vn.tsolve.common.security.Role;
import vn.tsolve.common.security.TSolveUser;
import vn.tsolve.ticket.entity.ImportBatch;
import vn.tsolve.ticket.entity.Ticket;
import vn.tsolve.ticket.entity.TicketEvidence;
import vn.tsolve.ticket.entity.TicketReviewDecision;
import vn.tsolve.ticket.entity.TicketSource;
import vn.tsolve.ticket.repository.ImportBatchRepository;
import vn.tsolve.ticket.repository.TicketEvidenceRepository;
import vn.tsolve.ticket.repository.TicketRepository;
import vn.tsolve.ticket.repository.TicketReviewDecisionRepository;
import vn.tsolve.ticket.service.ProjectAccess;

/** The Evidence files of a manual entry (AC-006.2, AC-006.6, AC-006.7) and what happens to them on a rejection. */
class ManualEntryEvidenceTest {

    private static final UUID PROJECT = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID PM_ID = UUID.fromString("00000000-0000-0000-0000-0000000000aa");
    private static final UUID TICKET = UUID.fromString("00000000-0000-0000-0000-000000000101");
    private static final TSolveUser PM =
            new TSolveUser(PM_ID, "pm@company.vn", "Nguyen Van An", Set.of(Role.PROJECT_MANAGER));
    private static final int TEN_MB = 10 * 1024 * 1024;
    private static final String KEY = "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}";
    private static final String ENTRY = "{\"projectId\":\"" + PROJECT + "\",\"ticketId\":\"T-1\",\"title\":\"Login"
            + " fails\",\"description\":\"Users cannot sign in\",\"symptoms\":\"Error 500\",\"rootCause\":\"Bad"
            + " token\",\"resolutionSteps\":\"Restart\",\"scope\":\"All users\"}";

    private final ManualEntryFileStorage storage = new ManualEntryFileStorage();
    private final EvidenceFiles evidence = new EvidenceFiles(Optional.of(storage));
    private final ProjectAccess projects = mock(ProjectAccess.class);
    private final TicketRepository tickets = mock(TicketRepository.class);
    private final TicketEvidenceRepository evidenceRows = mock(TicketEvidenceRepository.class);
    private final TicketReviewDecisionRepository decisions = mock(TicketReviewDecisionRepository.class);
    private final ImportBatchRepository batches = mock(ImportBatchRepository.class);
    private final ImportBatchRecorder recorder = mock(ImportBatchRecorder.class);
    private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
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
            new ManualEntryValidator(Clock.fixed(Instant.parse("2026-11-14T03:00:00Z"), ZoneOffset.UTC)),
            evidence,
            projects,
            tickets,
            evidenceRows,
            decisions,
            batches,
            recorder,
            events,
            txManager,
            mock(AuditService.class));

    @BeforeEach
    void setUp() {
        when(projects.managesActive(PM_ID.toString(), PROJECT)).thenReturn(true);
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

    private static MockMultipartFile file(String name, int size) {
        return new MockMultipartFile("files", name, "application/octet-stream", new byte[size]);
    }

    private MessageCodeException rejection(String entry, List<MultipartFile> files) {
        return catchThrowableOfType(() -> service.save(PM, entry, files), MessageCodeException.class);
    }

    @SuppressWarnings("unchecked")
    private List<TicketEvidence> savedEvidence() {
        ArgumentCaptor<List<TicketEvidence>> rows = ArgumentCaptor.forClass(List.class);
        verify(evidenceRows).saveAll(rows.capture());
        return rows.getValue();
    }

    @Test
    void storesEachFileAndRecordsItAsEvidenceOfTheNewTicket_AC_006_2() {
        service.save(PM, ENTRY, List.of(file("Report.PDF", "pdf body"), file("screen.jpeg", "jpeg body")));

        assertThat(storage.objects).hasSize(2);
        assertThat(savedEvidence())
                .satisfiesExactly(
                        pdf -> {
                            assertThat(pdf.getTicketId()).isEqualTo(TICKET);
                            assertThat(pdf.getFileName()).isEqualTo("Report.PDF");
                            assertThat(pdf.getFileType()).isEqualTo("pdf");
                            assertThat(pdf.getFileSize()).isEqualTo(8);
                            assertThat(storage.objects.get(pdf.getStorageKey()))
                                    .isEqualTo("pdf body".getBytes(StandardCharsets.UTF_8));
                        },
                        jpeg -> {
                            // D18: the type follows the extension; jpeg and jpg are one type
                            assertThat(jpeg.getFileType()).isEqualTo("jpg");
                            assertThat(jpeg.getFileSize()).isEqualTo(9);
                            assertThat(storage.objects).containsKey(jpeg.getStorageKey());
                        });
        assertThat(transactions).containsExactly("commit");
        assertThat(storage.deleted).isEmpty();
    }

    @Test
    void anEntryWithoutFilesStoresNothingAndRecordsNoEvidence_AC_006_2() {
        service.save(PM, ENTRY, List.of());

        assertThat(storage.objects).isEmpty();
        verify(evidenceRows, never()).saveAll(any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"a.png", "a.JPG", "a.jpeg", "a.pdf", "a.docx", "a.xlsx", "a.txt", "a.LOG"})
    void everyTypeOfD18IsAcceptedInAnyLetterCase_AC_006_6(String name) {
        assertThatCode(() -> evidence.check(List.of(file(name, "x")))).doesNotThrowAnyException();
    }

    @ParameterizedTest
    @ValueSource(strings = {"x.pdf.exe", "x.doc", "x.zip", "noextension", "x.", "x.png.", ""})
    void aFileOfAnotherTypeIsMsg34NamingItAndNothingIsStored_AC_006_6(String name) {
        MessageCodeException e = rejection(ENTRY, List.of(file("ok.png", "x"), file(name, "x")));

        assertThat(e.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(e.getCode()).isEqualTo("MSG34");
        assertThat(e.getParams()).containsEntry("file_name", name);
        assertThat(e.getField()).isEqualTo("files");
        assertThat(storage.objects).isEmpty();
        verifyNoInteractions(batches, recorder, decisions, evidenceRows, events);
        verify(tickets, never()).save(any(Ticket.class));
    }

    @Test
    void aFileNameIsOnlyStoredNeverUsedAsTheStorageKey_AC_006_6() {
        // edge 17: a path in the name does not reach the storage; the key is a new UUID
        service.save(PM, ENTRY, List.of(file("../../a.png", "x")));

        assertThat(savedEvidence()).singleElement().satisfies(row -> {
            assertThat(row.getFileName()).isEqualTo("../../a.png");
            assertThat(row.getFileType()).isEqualTo("png");
            assertThat(row.getStorageKey()).matches(KEY);
        });
    }

    @Test
    void aFileOf10MbIsAcceptedAndOneByteMoreIsMsg34_AC_006_6() {
        assertThatCode(() -> evidence.check(List.of(file("big.pdf", TEN_MB)))).doesNotThrowAnyException();

        MessageCodeException e = rejection(ENTRY, List.of(file("huge.pdf", TEN_MB + 1)));

        assertThat(e.getCode()).isEqualTo("MSG34");
        assertThat(e.getParams()).containsEntry("file_name", "huge.pdf");
        assertThat(storage.objects).isEmpty();
    }

    @Test
    void fiveFilesAreAcceptedAndASixthIsMsg72_AC_006_7() {
        List<MultipartFile> five = new ArrayList<>();
        for (int i = 1; i <= 5; i++) {
            five.add(file(i + ".txt", "x"));
        }
        assertThatCode(() -> evidence.check(five)).doesNotThrowAnyException();

        List<MultipartFile> six = new ArrayList<>(five);
        six.add(file("6.txt", "x"));
        MessageCodeException e = rejection(ENTRY, six);

        assertThat(e.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(e.getCode()).isEqualTo("MSG72");
        assertThat(e.getField()).isEqualTo("files");
        assertThat(storage.objects).isEmpty();
        verifyNoInteractions(batches, recorder);
    }

    @Test
    void anEntryWithAMissingFieldStoresNoFile_AC_006_3() {
        MessageCodeException e = rejection("{\"projectId\":\"" + PROJECT + "\"}", List.of(file("a.png", "x")));

        assertThat(e.getCode()).isEqualTo("MSG01");
        assertThat(storage.objects).isEmpty();
    }

    @Test
    void aProjectTheCallerDoesNotManageStoresNoFile_AC_006_9() {
        when(projects.managesActive(PM_ID.toString(), PROJECT)).thenReturn(false);

        assertThat(rejection(ENTRY, List.of(file("a.png", "x"))).getCode()).isEqualTo("MSG08");
        assertThat(storage.objects).isEmpty();
    }

    @Test
    void aTicketIdTakenBeforeTheSaveStoresNoFile_AC_006_8() {
        when(tickets.findExistingTicketIds(List.of("T-1"))).thenReturn(Set.of("T-1"));

        assertThat(rejection(ENTRY, List.of(file("a.png", "x"))).getCode()).isEqualTo("MSG31");
        assertThat(storage.objects).isEmpty();
    }

    @Test
    void aTicketIdTakenWhileSavingDeletesTheStoredFiles_AC_006_8() {
        when(tickets.save(any(Ticket.class)))
                .thenThrow(new DataIntegrityViolationException(
                        "could not execute statement",
                        new SQLException(
                                "ERROR: duplicate key value violates unique constraint \"uq_ticket_ticket_id\"",
                                "23505")));

        MessageCodeException e = rejection(ENTRY, List.of(file("a.png", "x"), file("b.pdf", "y")));

        assertThat(e.getCode()).isEqualTo("MSG31");
        assertThat(storage.deleted).hasSize(2);
        assertThat(storage.objects).isEmpty();
        verifyNoInteractions(recorder);
    }

    @Test
    void aFailureWhileSavingDeletesTheStoredFiles_AC_006_11() {
        when(decisions.save(any(TicketReviewDecision.class))).thenThrow(new QueryTimeoutException("timeout"));

        MessageCodeException e = rejection(ENTRY, List.of(file("a.png", "x"), file("b.pdf", "y")));

        assertThat(e.getStatus()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(e.getCode()).isEqualTo("MSG33");
        assertThat(transactions).containsExactly("rollback");
        assertThat(storage.objects).isEmpty();
        verify(recorder)
                .recordFailedWithoutPreview(eq(PROJECT), eq(PM_ID.toString()), eq(TicketSource.MANUAL), anyString());
    }

    @Test
    void storageDownAtTheThirdFileDeletesTheFirstTwoAndSavesNoTicket_AC_006_11() {
        // edge 19
        storage.failOnStore = 3;

        MessageCodeException e = rejection(
                ENTRY,
                List.of(
                        file("1.png", "a"),
                        file("2.png", "b"),
                        file("3.png", "c"),
                        file("4.png", "d"),
                        file("5.png", "e")));

        assertThat(e.getCode()).isEqualTo("MSG33");
        assertThat(e.getStatus()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(storage.deleted).hasSize(2);
        assertThat(storage.objects).isEmpty();
        assertThat(transactions).isEmpty();
        verify(tickets, never()).save(any(Ticket.class));
        ArgumentCaptor<String> error = ArgumentCaptor.forClass(String.class);
        verify(recorder)
                .recordFailedWithoutPreview(
                        eq(PROJECT), eq(PM_ID.toString()), eq(TicketSource.MANUAL), error.capture());
        assertThat(error.getValue()).contains("MSG33").doesNotContain("1.png", "T-1");
    }

    @Test
    void aFileThatCannotBeDeletedAfterAFailureStillAnswersMsg33_AC_006_11() {
        // the object stays as an orphan, never a Ticket without its file (design step 7)
        when(decisions.save(any(TicketReviewDecision.class))).thenThrow(new QueryTimeoutException("timeout"));
        storage.failDelete = true;

        assertThat(rejection(ENTRY, List.of(file("a.png", "x"), file("b.png", "y")))
                        .getCode())
                .isEqualTo("MSG33");
        assertThat(storage.deleted).hasSize(2);
    }

    @Test
    void filesWithoutAConfiguredStorageAreAFailureNotASilentLoss_AC_006_11() {
        ManualEntryServiceImpl withoutStorage = new ManualEntryServiceImpl(
                new ObjectMapper(),
                new ManualEntryValidator(Clock.fixed(Instant.parse("2026-11-14T03:00:00Z"), ZoneOffset.UTC)),
                new EvidenceFiles(Optional.empty()),
                projects,
                tickets,
                evidenceRows,
                decisions,
                batches,
                recorder,
                events,
                txManager,
                mock(AuditService.class));

        MessageCodeException e = catchThrowableOfType(
                () -> withoutStorage.save(PM, ENTRY, List.of(file("a.png", "x"))), MessageCodeException.class);

        assertThat(e.getCode()).isEqualTo("MSG33");
        verify(tickets, never()).save(any(Ticket.class));
    }
}
