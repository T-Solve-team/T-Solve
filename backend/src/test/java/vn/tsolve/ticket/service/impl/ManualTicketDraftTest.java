package vn.tsolve.ticket.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;
import vn.tsolve.common.audit.AuditService;
import vn.tsolve.common.exception.BusinessRuleException;
import vn.tsolve.common.exception.CodedException;
import vn.tsolve.common.exception.InvalidFieldsException;
import vn.tsolve.common.exception.NotFoundException;
import vn.tsolve.common.exception.ProblemDetails.FieldError;
import vn.tsolve.common.security.Role;
import vn.tsolve.common.security.TSolveUser;
import vn.tsolve.ticket.dto.DraftSavedResponse;
import vn.tsolve.ticket.dto.ManualTicketDraft;
import vn.tsolve.ticket.dto.ManualTicketRequest;
import vn.tsolve.ticket.dto.MessageRef;
import vn.tsolve.ticket.entity.ImportBatch;
import vn.tsolve.ticket.entity.ImportPreview;
import vn.tsolve.ticket.entity.ImportPreviewRow;
import vn.tsolve.ticket.entity.ImportType;
import vn.tsolve.ticket.entity.PreviewRowStatus;
import vn.tsolve.ticket.entity.Ticket;
import vn.tsolve.ticket.entity.TicketSource;
import vn.tsolve.ticket.exception.ProjectAccessDeniedException;
import vn.tsolve.ticket.mapper.ImportPreviewMapperImpl;
import vn.tsolve.ticket.repository.ImportBatchRepository;
import vn.tsolve.ticket.repository.ImportPreviewRepository;
import vn.tsolve.ticket.repository.ImportPreviewRowRepository;
import vn.tsolve.ticket.repository.TicketRepository;
import vn.tsolve.ticket.repository.TicketReviewDecisionRepository;
import vn.tsolve.ticket.service.ProjectAccess;

/** Manual entry Drafts (STORY-006, AC-006.9–AC-006.13). */
class ManualTicketDraftTest {

    static final UUID PROJECT = ManualTicketServiceImplTest.PROJECT;
    static final UUID OTHER_PROJECT = UUID.fromString("00000000-0000-0000-0000-000000000002");
    static final String OWNER = ManualTicketServiceImplTest.PM.getName();
    static final long DRAFT = 55L;
    static final Instant NOW = Instant.now(ManualTicketServiceImplTest.CLOCK);
    static final Instant EARLIER = Instant.parse("2026-11-01T00:00:00Z");

    final ImportPreviewRepository previews = mock(ImportPreviewRepository.class);
    final ImportPreviewRowRepository previewRows = mock(ImportPreviewRowRepository.class);
    final TicketRepository tickets = mock(TicketRepository.class);
    final TicketReviewDecisionRepository decisions = mock(TicketReviewDecisionRepository.class);
    final ImportBatchRepository batches = mock(ImportBatchRepository.class);
    final ProjectAccess projects = mock(ProjectAccess.class);

    final ManualTicketServiceImpl service = new ManualTicketServiceImpl(
            previews,
            previewRows,
            tickets,
            decisions,
            batches,
            projects,
            mock(ApplicationEventPublisher.class),
            mock(AuditService.class),
            ManualTicketServiceImplTest.CLOCK);

    static ManualTicketRequest draftRequest(UUID projectId, Long draftId) {
        // invalid on purpose: a Draft is not validated (AC-006.9)
        return new ManualTicketRequest(
                projectId, " ITSUP-1 ", "  ", "d".repeat(10_001), "not a date", "S", null, null, null, null, draftId);
    }

    static ManualTicketRequest validWithDraft(Long draftId) {
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
                draftId);
    }

    static ImportPreview draft(String owner, TicketSource source) {
        ImportPreview preview = source == TicketSource.MANUAL
                ? ImportPreview.manualDraft(owner, PROJECT, EARLIER)
                : new ImportPreview(owner, PROJECT, source, ImportType.WITH_SOLUTION, "t.csv", EARLIER);
        ReflectionTestUtils.setField(preview, "id", DRAFT);
        return preview;
    }

    static ImportPreviewRow draftRow() {
        return ImportPreviewRow.manualDraft(
                DRAFT,
                new ImportPreviewRow.Values(
                        "OLD-1", "Old title", null, null, "2026-10-01", "Old symptoms", null, null, null, null));
    }

    ImportPreview stored;

    @BeforeEach
    void setUp() {
        stored = draft(OWNER, TicketSource.MANUAL);
        when(previews.findByIdAndOwner(anyLong(), anyString())).thenReturn(Optional.empty());
        when(previews.findByIdAndOwner(DRAFT, OWNER)).thenReturn(Optional.of(stored));
        when(previews.lockByIdAndOwner(anyLong(), anyString())).thenReturn(Optional.empty());
        when(previews.lockByIdAndOwner(DRAFT, OWNER)).thenReturn(Optional.of(stored));
        when(previewRows.findById(new ImportPreviewRow.Id(DRAFT, 1))).thenReturn(Optional.of(draftRow()));
        when(projects.managesActive(OWNER, PROJECT)).thenReturn(true);
        when(projects.managesActive(OWNER, OTHER_PROJECT)).thenReturn(true);
        when(previews.save(any())).thenAnswer(call -> {
            ImportPreview preview = call.getArgument(0);
            ReflectionTestUtils.setField(preview, "id", 77L);
            return preview;
        });
        when(tickets.findExistingTicketIds(anyCollection())).thenReturn(Set.of());
        when(batches.save(any())).thenAnswer(call -> {
            ImportBatch batch = call.getArgument(0);
            ReflectionTestUtils.setField(batch, "id", 7L);
            return batch;
        });
        when(tickets.saveAndFlush(any())).thenAnswer(call -> {
            Ticket ticket = call.getArgument(0);
            ReflectionTestUtils.setField(ticket, "id", ManualTicketServiceImplTest.TICKET);
            return ticket;
        });
    }

    @Test
    void AC_006_9_savesTheFieldsAsTypedAsAManualDraftWithItsSaveTime() {
        DraftSavedResponse saved = service.saveDraft(ManualTicketServiceImplTest.PM, draftRequest(PROJECT, null));

        ArgumentCaptor<ImportPreview> preview = ArgumentCaptor.forClass(ImportPreview.class);
        verify(previews).save(preview.capture());
        ImportPreview p = preview.getValue();
        assertThat(p.getOwner()).isEqualTo(OWNER);
        assertThat(p.getProjectId()).isEqualTo(PROJECT);
        assertThat(p.getSource()).isEqualTo(TicketSource.MANUAL);
        assertThat(p.getImportType()).isEqualTo(ImportType.WITH_SOLUTION);
        assertThat(p.getFileName()).isNull();
        assertThat(p.getDraftSavedAt()).isEqualTo(NOW);
        ArgumentCaptor<ImportPreviewRow> row = ArgumentCaptor.forClass(ImportPreviewRow.class);
        verify(previewRows).save(row.capture());
        assertThat(row.getValue().getId()).isEqualTo(new ImportPreviewRow.Id(77L, 1));
        assertThat(row.getValue().getStatus()).isEqualTo(PreviewRowStatus.VALID);
        assertThat(row.getValue().getValues())
                .isEqualTo(new ImportPreviewRow.Values(
                        " ITSUP-1 ", "  ", "d".repeat(10_001), null, "not a date", "S", null, null, null, null));
        assertThat(saved).isEqualTo(new DraftSavedResponse(77L, NOW, new MessageRef("MSG41", Map.of())));
    }

    @Test
    void AC_006_9_rejectsADraftWithoutProjectWithMsg01() {
        InvalidFieldsException e = catchThrowableOfType(
                () -> service.saveDraft(ManualTicketServiceImplTest.PM, draftRequest(null, null)),
                InvalidFieldsException.class);

        assertThat(e.getErrors())
                .containsExactly(new FieldError("projectId", "MSG01", Map.of("field_name", "projectId")));
        verify(previews, never()).save(any());
    }

    @Test
    void AC_006_9_rejectsADraftOfAProjectTheCallerDoesNotManageWithMsg08() {
        when(projects.managesActive(OWNER, PROJECT)).thenReturn(false);

        assertThatThrownBy(() -> service.saveDraft(ManualTicketServiceImplTest.PM, draftRequest(PROJECT, null)))
                .isInstanceOf(ProjectAccessDeniedException.class);
        assertThatThrownBy(() -> service.saveDraft(ManualTicketServiceImplTest.PM, draftRequest(PROJECT, DRAFT)))
                .isInstanceOf(ProjectAccessDeniedException.class);
        verify(previews, never()).save(any());
        assertThat(stored.getDraftSavedAt()).isEqualTo(EARLIER);
    }

    @Test
    void AC_006_10_updatesTheSameDraftItsFieldsProjectAndSaveTime() {
        ImportPreviewRow row = draftRow();
        when(previewRows.findById(new ImportPreviewRow.Id(DRAFT, 1))).thenReturn(Optional.of(row));

        DraftSavedResponse saved =
                service.saveDraft(ManualTicketServiceImplTest.PM, draftRequest(OTHER_PROJECT, DRAFT));

        verify(previews, never()).save(any());
        verify(previewRows, never()).save(any());
        assertThat(stored.getProjectId()).isEqualTo(OTHER_PROJECT);
        assertThat(stored.getDraftSavedAt()).isEqualTo(NOW);
        assertThat(row.getValues())
                .isEqualTo(new ImportPreviewRow.Values(
                        " ITSUP-1 ", "  ", "d".repeat(10_001), null, "not a date", "S", null, null, null, null));
        assertThat(saved.previewId()).isEqualTo(DRAFT);
    }

    @Test
    void AC_006_11_returnsTheDraftsProjectAndEveryFieldAsSaved() {
        ManualTicketDraft draft = service.getDraft(ManualTicketServiceImplTest.PM, DRAFT);

        assertThat(draft)
                .isEqualTo(new ManualTicketDraft(
                        DRAFT,
                        PROJECT,
                        "OLD-1",
                        "Old title",
                        null,
                        "2026-10-01",
                        "Old symptoms",
                        null,
                        null,
                        null,
                        null,
                        EARLIER));
    }

    @Test
    void AC_006_11_rejectsADraftOfAProjectTheCallerNoLongerManagesWithMsg08() {
        when(projects.managesActive(OWNER, PROJECT)).thenReturn(false);

        assertThatThrownBy(() -> service.getDraft(ManualTicketServiceImplTest.PM, DRAFT))
                .isInstanceOf(ProjectAccessDeniedException.class);
    }

    @Test
    void AC_006_12_deletesTheDraftWhenTheSaveSucceeds() {
        service.create(ManualTicketServiceImplTest.PM, validWithDraft(DRAFT));

        verify(tickets).saveAndFlush(any());
        verify(previews).delete(stored);
    }

    @Test
    void AC_006_12_keepsTheDraftWhenTheSaveIsRejected() {
        when(tickets.findExistingTicketIds(List.of("ITSUP-1"))).thenReturn(Set.of("ITSUP-1"));
        assertThatThrownBy(() -> service.create(ManualTicketServiceImplTest.PM, validWithDraft(DRAFT)))
                .isInstanceOf(BusinessRuleException.class);

        ManualTicketRequest invalid = draftRequest(PROJECT, DRAFT);
        assertThatThrownBy(() -> service.create(ManualTicketServiceImplTest.PM, invalid))
                .isInstanceOf(InvalidFieldsException.class);

        when(projects.managesActive(OWNER, PROJECT)).thenReturn(false);
        assertThatThrownBy(() -> service.create(ManualTicketServiceImplTest.PM, validWithDraft(DRAFT)))
                .isInstanceOf(ProjectAccessDeniedException.class);

        verify(previews, never()).delete(any());
        assertThat(stored.getDraftSavedAt()).isEqualTo(EARLIER);
    }

    @Test
    void AC_006_13_answers404Msg04ForAMissingAnotherUsersOrNonManualDraft() {
        ImportPreview csv = draft(OWNER, TicketSource.CSV);
        when(previews.findByIdAndOwner(88L, OWNER)).thenReturn(Optional.of(csv));
        when(previews.lockByIdAndOwner(88L, OWNER)).thenReturn(Optional.of(csv));

        for (long id : List.of(99L, 88L)) {
            assertMsg04(() -> service.getDraft(ManualTicketServiceImplTest.PM, id));
            assertMsg04(() -> service.saveDraft(ManualTicketServiceImplTest.PM, draftRequest(PROJECT, id)));
            assertMsg04(() -> service.create(ManualTicketServiceImplTest.PM, validWithDraft(id)));
        }
        // another user's Draft: the lookups are by owner, so it is not found
        TSolveUser other = new TSolveUser(
                UUID.fromString("44444444-4444-4444-4444-444444444444"),
                "other-pm@company.vn",
                "Other PM",
                Set.of(Role.PROJECT_MANAGER));
        when(projects.managesActive(other.getName(), PROJECT)).thenReturn(true);
        assertMsg04(() -> service.getDraft(other, DRAFT));
        assertMsg04(() -> service.saveDraft(other, draftRequest(PROJECT, DRAFT)));
        assertMsg04(() -> service.create(other, validWithDraft(DRAFT)));
        assertThat(stored.getDraftSavedAt()).isEqualTo(EARLIER);
        verify(tickets, never()).saveAndFlush(any());
        verify(previews, never()).save(any());
    }

    private static void assertMsg04(org.assertj.core.api.ThrowableAssert.ThrowingCallable call) {
        NotFoundException e = catchThrowableOfType(call, NotFoundException.class);
        assertThat(e).isNotNull();
        assertThat(((CodedException) e).getCode()).isEqualTo("MSG04");
    }

    @Test
    void AC_006_13_deleteRemovesAManualDraftAndACsvPreviewStays404() {
        ImportDraftServiceImpl drafts =
                new ImportDraftServiceImpl(previews, previewRows, projects, ManualTicketServiceImplTest.CLOCK);
        ImportPreview csv = draft(OWNER, TicketSource.CSV);
        csv.saveAsDraft(EARLIER);
        when(previews.findByIdAndOwner(88L, OWNER)).thenReturn(Optional.of(csv));

        drafts.delete(OWNER, DRAFT);
        verify(previews).delete(stored);

        assertMsg04(() -> drafts.delete(OWNER, 88L));
        assertMsg04(() -> drafts.delete("someone-else", DRAFT));
    }

    @Test
    void AC_006_13_previewEndpointsAnswer404ForAManualDraft() {
        ImportPreviewServiceImpl previewService =
                new ImportPreviewServiceImpl(previews, previewRows, new ImportPreviewMapperImpl(), projects);
        ImportConfirmServiceImpl confirms = new ImportConfirmServiceImpl(
                previews,
                previewRows,
                tickets,
                decisions,
                batches,
                mock(ImportBatchRecorder.class),
                projects,
                mock(ApplicationEventPublisher.class),
                mock(org.springframework.transaction.PlatformTransactionManager.class),
                mock(AuditService.class));

        assertThatThrownBy(() -> previewService.preview(OWNER, DRAFT)).isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> previewService.row(OWNER, DRAFT, 1)).isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> confirms.confirm(OWNER, DRAFT)).isInstanceOf(NotFoundException.class);
        verify(tickets, never()).saveAll(any());
    }
}
