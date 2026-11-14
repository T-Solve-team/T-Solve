package vn.tsolve.ticket.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** The entities of a manual entry (KAN-133): the Ticket, its Evidence and its Draft. */
class ManualEntryTicketTest {

    private static final UUID PROJECT = UUID.randomUUID();
    private static final UUID WRITER = UUID.randomUUID();
    private static final Ticket.Solution SOLUTION =
            new Ticket.Solution("symptoms", "root cause", "steps", null, "scope");

    @Test
    void manualTicketIsPublishedWithItsWriter_AC_006_1() {
        LocalDate resolved = LocalDate.of(2026, 11, 1);

        Ticket ticket = Ticket.enteredManually(
                "OPS-1", PROJECT, 7L, "Title", "Description", "Dang Nhat Minh", WRITER, resolved, SOLUTION);

        assertThat(ticket.getSource()).isEqualTo(TicketSource.MANUAL);
        assertThat(ticket.getStatus()).isEqualTo(TicketStatus.PUBLISHED);
        assertThat(ticket.getTicketId()).isEqualTo("OPS-1");
        assertThat(ticket.getProjectId()).isEqualTo(PROJECT);
        assertThat(ticket.getImportBatchId()).isEqualTo(7L);
        assertThat(ticket.getTitle()).isEqualTo("Title");
        assertThat(ticket.getDescription()).isEqualTo("Description");
        assertThat(ticket.getWriter()).isEqualTo("Dang Nhat Minh");
        assertThat(ticket.getWriterId()).isEqualTo(WRITER);
        assertThat(ticket.getResolvedDate()).isEqualTo(resolved);
        assertThat(ticket.getSolution()).isEqualTo(SOLUTION);
    }

    @Test
    void writerNameLongerThanTheColumnIsCutTo100_AC_006_1() {
        Ticket ticket = Ticket.enteredManually(
                "OPS-2", PROJECT, 7L, "Title", "Description", "n".repeat(120), WRITER, null, SOLUTION);

        assertThat(ticket.getWriter()).isEqualTo("n".repeat(100));
        assertThat(ticket.getResolvedDate()).isNull();
    }

    @Test
    void evidenceKeepsTheStorageKeyNameTypeAndSize_AC_006_2() {
        UUID ticketId = UUID.randomUUID();

        TicketEvidence evidence = TicketEvidence.of(ticketId, "key-1", "trace.log", "log", 2048);

        assertThat(evidence.getId()).isNotNull();
        assertThat(evidence.getTicketId()).isEqualTo(ticketId);
        assertThat(evidence.getStorageKey()).isEqualTo("key-1");
        assertThat(evidence.getFileName()).isEqualTo("trace.log");
        assertThat(evidence.getFileType()).isEqualTo("log");
        assertThat(evidence.getFileSize()).isEqualTo(2048);
    }

    @Test
    void manualDraftIsADraftOfSourceManualFromItsFirstSave_AC_007_1() {
        Instant now = Instant.parse("2026-11-14T03:00:00Z");

        ImportPreview draft = ImportPreview.manualDraft("owner", PROJECT, now);

        assertThat(draft.getSource()).isEqualTo(TicketSource.MANUAL);
        assertThat(draft.getImportType()).isEqualTo(ImportType.WITH_SOLUTION);
        assertThat(draft.getOwner()).isEqualTo("owner");
        assertThat(draft.getProjectId()).isEqualTo(PROJECT);
        assertThat(draft.getFileName()).isNull();
        assertThat(draft.isDraft()).isTrue();
        assertThat(draft.getDraftSavedAt()).isEqualTo(now);
        assertThat(draft.getEvidence()).isEmpty();
    }

    @Test
    void manualDraftEvidenceListIsReplacedOnEachSave_AC_007_5() {
        ImportPreview draft = ImportPreview.manualDraft("owner", PROJECT, Instant.parse("2026-11-14T03:00:00Z"));
        DraftEvidence kept = new DraftEvidence(UUID.randomUUID(), "key-1", "a.png", "png", 10);

        draft.replaceEvidence(List.of(kept));

        assertThat(draft.getEvidence()).containsExactly(kept);
    }
}
