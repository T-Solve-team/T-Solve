package vn.tsolve.ticket.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import vn.tsolve.common.audit.AuditAction;
import vn.tsolve.common.audit.AuditService;
import vn.tsolve.common.exception.BusinessRuleException;
import vn.tsolve.common.exception.InvalidFieldsException;
import vn.tsolve.common.exception.ProblemDetails.FieldError;
import vn.tsolve.common.security.Role;
import vn.tsolve.common.security.TSolveUser;
import vn.tsolve.platform.service.FileStorage;
import vn.tsolve.ticket.dto.ImportResultResponse;
import vn.tsolve.ticket.dto.ManualTicketRequest;
import vn.tsolve.ticket.dto.MessageRef;
import vn.tsolve.ticket.entity.ImportBatch;
import vn.tsolve.ticket.entity.ImportBatchStatus;
import vn.tsolve.ticket.entity.ReviewDecision;
import vn.tsolve.ticket.entity.Ticket;
import vn.tsolve.ticket.entity.TicketReviewDecision;
import vn.tsolve.ticket.entity.TicketSource;
import vn.tsolve.ticket.entity.TicketStatus;
import vn.tsolve.ticket.event.TicketsPublishedEvent;
import vn.tsolve.ticket.exception.ProjectAccessDeniedException;
import vn.tsolve.ticket.repository.ImportBatchRepository;
import vn.tsolve.ticket.repository.ImportPreviewEvidenceRepository;
import vn.tsolve.ticket.repository.ImportPreviewRepository;
import vn.tsolve.ticket.repository.ImportPreviewRowRepository;
import vn.tsolve.ticket.repository.TicketEvidenceRepository;
import vn.tsolve.ticket.repository.TicketRepository;
import vn.tsolve.ticket.repository.TicketReviewDecisionRepository;
import vn.tsolve.ticket.service.ProjectAccess;

class ManualTicketServiceImplTest {

    static final UUID PROJECT = UUID.fromString("00000000-0000-0000-0000-000000000001");
    static final TSolveUser PM = new TSolveUser(
            UUID.fromString("11111111-1111-1111-1111-111111111111"),
            "pm@company.vn",
            "Nguyễn Văn An",
            Set.of(Role.PROJECT_MANAGER));
    // 2026-11-14 17:30 UTC is already 2026-11-15 00:30 in Vietnam
    static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-14T17:30:00Z"), ZoneId.of("Asia/Ho_Chi_Minh"));
    static final UUID TICKET = UUID.fromString("22222222-2222-2222-2222-222222222222");

    final TicketRepository tickets = mock(TicketRepository.class);
    final TicketReviewDecisionRepository decisions = mock(TicketReviewDecisionRepository.class);
    final ImportBatchRepository batches = mock(ImportBatchRepository.class);
    final ProjectAccess projects = mock(ProjectAccess.class);
    final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
    final AuditService audit = mock(AuditService.class);

    final ManualTicketServiceImpl service = new ManualTicketServiceImpl(
            mock(ImportPreviewRepository.class),
            mock(ImportPreviewRowRepository.class),
            tickets,
            decisions,
            batches,
            projects,
            events,
            audit,
            CLOCK,
            mock(ImportPreviewEvidenceRepository.class),
            mock(TicketEvidenceRepository.class),
            new StaticListableBeanFactory().getBeanProvider(FileStorage.class),
            mock(PlatformTransactionManager.class));

    static ManualTicketRequest valid() {
        return new ManualTicketRequest(
                PROJECT,
                " ITSUP-1 ",
                " Login fails ",
                " Users get 500 ",
                "2026-11-15",
                " Error 500 ",
                " Bad token ",
                " Restart ",
                null,
                " All users ",
                null,
                null);
    }

    static ManualTicketRequest with(
            String ticketId,
            String title,
            String description,
            String resolvedDate,
            String symptoms,
            String rootCause,
            String resolutionSteps,
            String workaround,
            String scope) {
        return new ManualTicketRequest(
                PROJECT,
                ticketId,
                title,
                description,
                resolvedDate,
                symptoms,
                rootCause,
                resolutionSteps,
                workaround,
                scope,
                null,
                null);
    }

    @BeforeEach
    void setUp() {
        when(projects.managesActive(PM.getName(), PROJECT)).thenReturn(true);
        when(tickets.findExistingTicketIds(anyCollection())).thenReturn(Set.of());
        when(batches.save(any())).thenAnswer(call -> {
            ImportBatch batch = call.getArgument(0);
            ReflectionTestUtils.setField(batch, "id", 7L);
            return batch;
        });
        when(tickets.saveAndFlush(any())).thenAnswer(call -> {
            Ticket ticket = call.getArgument(0);
            ReflectionTestUtils.setField(ticket, "id", TICKET);
            return ticket;
        });
    }

    private List<FieldError> fieldErrorsOf(ManualTicketRequest request) {
        InvalidFieldsException e =
                catchThrowableOfType(() -> service.create(PM, request), InvalidFieldsException.class);
        assertThat(e).as("rejected with field errors").isNotNull();
        assertThat(e.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
        return e.getErrors();
    }

    private void verifyNothingSaved() {
        verify(tickets, never()).saveAndFlush(any());
        verify(batches, never()).save(any());
        verify(decisions, never()).save(any());
        verify(events, never()).publishEvent(any());
    }

    @Test
    void AC_006_1_savesPublishedTicketWithApproveDecisionAndBatch() {
        ImportResultResponse result = service.create(PM, valid());

        ArgumentCaptor<Ticket> ticket = ArgumentCaptor.forClass(Ticket.class);
        verify(tickets).saveAndFlush(ticket.capture());
        Ticket t = ticket.getValue();
        assertThat(t.getTicketId()).isEqualTo("ITSUP-1");
        assertThat(t.getProjectId()).isEqualTo(PROJECT);
        assertThat(t.getSource()).isEqualTo(TicketSource.MANUAL);
        assertThat(t.getStatus()).isEqualTo(TicketStatus.PUBLISHED);
        assertThat(t.getTitle()).isEqualTo("Login fails");
        assertThat(t.getDescription()).isEqualTo("Users get 500");
        assertThat(t.getResolvedDate()).isEqualTo(LocalDate.of(2026, 11, 15));
        assertThat(t.getWriter()).isEqualTo("Nguyễn Văn An");
        assertThat(t.getWriterId()).isEqualTo(PM.id());
        assertThat(t.getImportBatchId()).isEqualTo(7L);
        assertThat(t.getSolution())
                .isEqualTo(new Ticket.Solution("Error 500", "Bad token", "Restart", null, "All users"));

        ArgumentCaptor<ImportBatch> batch = ArgumentCaptor.forClass(ImportBatch.class);
        verify(batches).save(batch.capture());
        ImportBatch b = batch.getValue();
        assertThat(b.getStatus()).isEqualTo(ImportBatchStatus.COMPLETED);
        assertThat(b.getSource()).isEqualTo(TicketSource.MANUAL);
        assertThat(b.getProjectId()).isEqualTo(PROJECT);
        assertThat(b.getPerformedBy()).isEqualTo(PM.getName());
        assertThat(b.getFileName()).isNull();
        assertThat(b.getPublishedCount()).isEqualTo(1);
        assertThat(b.getErrorCount()).isZero();
        assertThat(b.getRemovedCount()).isZero();

        ArgumentCaptor<TicketReviewDecision> decision = ArgumentCaptor.forClass(TicketReviewDecision.class);
        verify(decisions).save(decision.capture());
        assertThat(decision.getValue())
                .extracting("ticketId", "reviewer", "decision")
                .containsExactly(TICKET, PM.getName(), ReviewDecision.APPROVE);
        verify(audit).record(AuditAction.IMPORT, "PROJECT", PROJECT, Map.of("importBatchId", 7L, "created", 1));

        assertThat(result)
                .isEqualTo(new ImportResultResponse(
                        7L, 1, 0, new MessageRef("MSG32", Map.of("created", 1, "skipped", 0)), null));
    }

    @Test
    void AC_006_1_cutsALongFullNameToTheWriterColumn() {
        TSolveUser longName = new TSolveUser(PM.id(), PM.email(), "A".repeat(99) + "😀😀", PM.roles());
        when(projects.managesActive(longName.getName(), PROJECT)).thenReturn(true);

        service.create(longName, valid());

        ArgumentCaptor<Ticket> ticket = ArgumentCaptor.forClass(Ticket.class);
        verify(tickets).saveAndFlush(ticket.capture());
        assertThat(ticket.getValue().getWriter()).isEqualTo("A".repeat(99) + "😀");
    }

    @Test
    void AC_006_2_publishesOneTicketsPublishedEventWithTheNewTicket() {
        service.create(PM, valid());

        verify(events).publishEvent(new TicketsPublishedEvent(List.of(TICKET)));
    }

    @Test
    void AC_006_3_rejectsEveryMissingOrBlankFieldWithMsg01() {
        ManualTicketRequest request = new ManualTicketRequest(
                null, " ", null, "", "  ", "\t", null, " ", "kept as optional", null, null, null);

        List<FieldError> errors = fieldErrorsOf(request);

        assertThat(errors)
                .extracting(FieldError::field, FieldError::code)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("projectId", "MSG01"),
                        org.assertj.core.groups.Tuple.tuple("ticketId", "MSG01"),
                        org.assertj.core.groups.Tuple.tuple("title", "MSG01"),
                        org.assertj.core.groups.Tuple.tuple("description", "MSG01"),
                        org.assertj.core.groups.Tuple.tuple("symptoms", "MSG01"),
                        org.assertj.core.groups.Tuple.tuple("rootCause", "MSG01"),
                        org.assertj.core.groups.Tuple.tuple("resolutionSteps", "MSG01"),
                        org.assertj.core.groups.Tuple.tuple("scope", "MSG01"));
        assertThat(errors.get(1).params()).isEqualTo(Map.of("field_name", "ticketId"));
        verifyNoInteractions(projects);
        verifyNothingSaved();
    }

    @Test
    void AC_006_4_rejectsTooLongFieldsAndSolutionWithMsg02AndTheLimit() {
        ManualTicketRequest request = with(
                "😀".repeat(51),
                "t".repeat(201),
                "d".repeat(10_001),
                null,
                "s".repeat(5_000),
                "r".repeat(2_000),
                "x".repeat(2_000),
                "w".repeat(1_000),
                "a".repeat(1));

        List<FieldError> errors = fieldErrorsOf(request);

        assertThat(errors)
                .containsExactly(
                        new FieldError("ticketId", "MSG02", Map.of("field_name", "ticketId", "max_length", 50)),
                        new FieldError("title", "MSG02", Map.of("field_name", "title", "max_length", 200)),
                        new FieldError(
                                "description", "MSG02", Map.of("field_name", "description", "max_length", 10_000)),
                        new FieldError("solution", "MSG02", Map.of("field_name", "Solution", "max_length", 10_000)));
        verifyNothingSaved();
    }

    @Test
    void AC_006_4_acceptsFieldsAtTheirLimitCountingCodePoints() {
        service.create(
                PM,
                with(
                        "😀".repeat(50),
                        "t".repeat(200),
                        "d".repeat(10_000),
                        null,
                        "s".repeat(5_000),
                        "r".repeat(2_000),
                        "x".repeat(2_000),
                        "w".repeat(999),
                        "a"));

        verify(tickets).saveAndFlush(any());
    }

    @Test
    void AC_006_5_rejectsAFutureOrUnreadableResolvedDateWithMsg39() {
        for (String date : List.of("2026-11-16", "16/11/2026", "2026-02-30")) {
            ManualTicketRequest request = with("ITSUP-1", "Title", "Desc", date, "S", "R", "Steps", null, "Scope");

            assertThat(fieldErrorsOf(request))
                    .as(date)
                    .containsExactly(new FieldError("resolvedDate", "MSG39", Map.of()));
        }
        verifyNothingSaved();
    }

    @Test
    void AC_006_5_allowsAMissingResolvedDateAndTodayInVietnam() {
        service.create(PM, with("ITSUP-1", "Title", "Desc", "  ", "S", "R", "Steps", null, "Scope"));
        service.create(PM, with("ITSUP-2", "Title", "Desc", "2026-11-15", "S", "R", "Steps", null, "Scope"));

        ArgumentCaptor<Ticket> ticket = ArgumentCaptor.forClass(Ticket.class);
        verify(tickets, org.mockito.Mockito.times(2)).saveAndFlush(ticket.capture());
        assertThat(ticket.getAllValues())
                .extracting(Ticket::getResolvedDate)
                .containsExactly(null, LocalDate.of(2026, 11, 15));
    }

    @Test
    void AC_006_6_reportsEveryInvalidFieldInOneAnswer() {
        ManualTicketRequest request =
                with(null, "t".repeat(201), "Desc", "2030-01-01", "S", null, "Steps", null, "Scope");

        assertThat(fieldErrorsOf(request))
                .extracting(FieldError::field, FieldError::code)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("ticketId", "MSG01"),
                        org.assertj.core.groups.Tuple.tuple("title", "MSG02"),
                        org.assertj.core.groups.Tuple.tuple("resolvedDate", "MSG39"),
                        org.assertj.core.groups.Tuple.tuple("rootCause", "MSG01"));
    }

    @Test
    void AC_006_7_rejectsATakenTicketIdWithMsg31AndSavesNothing() {
        when(tickets.findExistingTicketIds(List.of("ITSUP-1"))).thenReturn(Set.of("ITSUP-1"));

        BusinessRuleException e = catchThrowableOfType(() -> service.create(PM, valid()), BusinessRuleException.class);

        assertThat(e.getCode()).isEqualTo("MSG31");
        assertThat(e.getParams()).isEqualTo(Map.of("ticket_id", "ITSUP-1"));
        assertThat(e.getField()).isEqualTo("ticketId");
        verifyNothingSaved();
    }

    @Test
    void AC_006_7_answersMsg31WhenAnotherRequestTakesTheTicketIdMeanwhile() {
        doThrow(new DataIntegrityViolationException("duplicate", new SQLException("duplicate key", "23505")))
                .when(tickets)
                .saveAndFlush(any());

        BusinessRuleException e = catchThrowableOfType(() -> service.create(PM, valid()), BusinessRuleException.class);

        assertThat(e.getCode()).isEqualTo("MSG31");
        assertThat(e.getParams()).isEqualTo(Map.of("ticket_id", "ITSUP-1"));
        verify(events, never()).publishEvent(any());
        verify(decisions, never()).save(any());
    }

    @Test
    void AC_006_7_letsAnotherDatabaseErrorThrough() {
        DataIntegrityViolationException notNull =
                new DataIntegrityViolationException("null", new SQLException("not null", "23502"));
        doThrow(notNull).when(tickets).saveAndFlush(any());

        assertThat(catchThrowableOfType(() -> service.create(PM, valid()), DataIntegrityViolationException.class))
                .isSameAs(notNull);
    }

    @Test
    void AC_006_8_rejectsAProjectTheCallerDoesNotManageWithMsg08() {
        when(projects.managesActive(PM.getName(), PROJECT)).thenReturn(false);

        ProjectAccessDeniedException e =
                catchThrowableOfType(() -> service.create(PM, valid()), ProjectAccessDeniedException.class);

        assertThat(e.getCode()).isEqualTo("MSG08");
        assertThat(e.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
        verifyNothingSaved();
    }
}
