package vn.tsolve.ticket.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import vn.tsolve.common.error.MessageCodeException;
import vn.tsolve.ticket.csv.CsvRowValidator;
import vn.tsolve.ticket.csv.CsvTicketParser;
import vn.tsolve.ticket.dto.ImportPreviewResponse;
import vn.tsolve.ticket.dto.MessageRef;
import vn.tsolve.ticket.entity.ImportPreview;
import vn.tsolve.ticket.entity.ImportPreviewRow;
import vn.tsolve.ticket.entity.PreviewRowStatus;
import vn.tsolve.ticket.entity.TicketSource;
import vn.tsolve.ticket.mapper.ImportPreviewMapperImpl;
import vn.tsolve.ticket.repository.ImportPreviewRepository;
import vn.tsolve.ticket.repository.ImportPreviewRowRepository;
import vn.tsolve.ticket.repository.TicketRepository;
import vn.tsolve.ticket.service.ProjectAccess;

class CsvImportServiceImplTest {

    private static final String PM = "pm@company.vn";
    private static final String HEADER = "ticket_id,title,symptoms,root_cause,resolution_steps,scope\n";
    private static final Clock CLOCK =
            Clock.fixed(Instant.parse("2026-10-22T03:00:00Z"), ZoneId.of("Asia/Ho_Chi_Minh"));

    private final ImportPreviewRepository previews = mock(ImportPreviewRepository.class);
    private final ImportPreviewRowRepository rows = mock(ImportPreviewRowRepository.class);
    private final TicketRepository tickets = mock(TicketRepository.class);
    private final ProjectAccess projects = mock(ProjectAccess.class);

    private final CsvImportServiceImpl service = new CsvImportServiceImpl(
            new CsvTicketParser(),
            new CsvRowValidator(CLOCK),
            previews,
            rows,
            tickets,
            projects,
            new ImportPreviewMapperImpl(),
            CLOCK);

    @BeforeEach
    void setUp() {
        when(projects.managesActive(PM, 1L)).thenReturn(true);
        when(tickets.findExistingTicketIds(anyCollection())).thenReturn(Set.of());
        when(previews.save(any(ImportPreview.class))).thenAnswer(call -> {
            ImportPreview p = call.getArgument(0);
            ReflectionTestUtils.setField(p, "id", 42L);
            return p;
        });
    }

    private static MockMultipartFile csv(String name, String body) {
        return new MockMultipartFile("file", name, "text/csv", body.getBytes(StandardCharsets.UTF_8));
    }

    private static String rowsOf(String... ids) {
        StringBuilder b = new StringBuilder(HEADER);
        for (String id : ids) {
            b.append(id).append(",Login fails,Error 500,Bad token,Restart,All users\n");
        }
        return b.toString();
    }

    @SuppressWarnings("unchecked")
    private List<ImportPreviewRow> savedRows() {
        ArgumentCaptor<List<ImportPreviewRow>> captor = ArgumentCaptor.forClass(List.class);
        verify(rows).saveAll(captor.capture());
        return captor.getValue();
    }

    @Test
    void createsPreviewWithCountsAndFirstPage_AC_001_1() {
        ImportPreviewResponse response = service.upload(PM, 1L, csv("tickets.csv", rowsOf("T-1", "T-2")));

        ArgumentCaptor<ImportPreview> preview = ArgumentCaptor.forClass(ImportPreview.class);
        verify(previews).save(preview.capture());
        assertThat(preview.getValue().getOwner()).isEqualTo(PM);
        assertThat(preview.getValue().getProjectId()).isEqualTo(1L);
        assertThat(preview.getValue().getSource()).isEqualTo(TicketSource.CSV);
        assertThat(preview.getValue().getFileName()).isEqualTo("tickets.csv");
        assertThat(savedRows()).extracting(ImportPreviewRow::getRowNo).containsExactly(1, 2);

        assertThat(response.previewId()).isEqualTo(42L);
        assertThat(response.projectId()).isEqualTo(1L);
        assertThat(response.validCount()).isEqualTo(2);
        assertThat(response.errorCount()).isZero();
        assertThat(response.removedCount()).isZero();
        assertThat(response.message()).isNull();
        assertThat(response.rows().getContent()).hasSize(2);
        assertThat(response.rows().getMetadata().totalElements()).isEqualTo(2);
    }

    @Test
    void firstPageHoldsFiftyRows_AC_001_1() {
        String[] ids = new String[120];
        for (int i = 0; i < ids.length; i++) {
            ids[i] = "T-" + i;
        }

        ImportPreviewResponse response = service.upload(PM, 1L, csv("tickets.csv", rowsOf(ids)));

        assertThat(response.rows().getContent()).hasSize(50);
        assertThat(response.rows().getMetadata().totalElements()).isEqualTo(120);
        assertThat(response.rows().getMetadata().totalPages()).isEqualTo(3);
    }

    @Test
    void rejectsFileNotEndingInCsv_AC_001_2() {
        for (String name : new String[] {"tickets.xlsx", "tickets.csv.txt", "tickets", ""}) {
            MessageCodeException e = catchThrowableOfType(
                    () -> service.upload(PM, 1L, csv(name, rowsOf("T-1"))), MessageCodeException.class);

            assertThat(e.getCode()).as(name).isEqualTo("MSG27");
            assertThat(e.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
        }
        verifyNoInteractions(previews, rows);
    }

    @Test
    void acceptsUpperCaseExtension_AC_001_2() {
        service.upload(PM, 1L, csv("TICKETS.CSV", rowsOf("T-1")));

        verify(previews).save(any(ImportPreview.class));
    }

    @Test
    void rejectsFileOverTenMegabytes_AC_001_4() {
        MockMultipartFile big =
                new MockMultipartFile("file", "tickets.csv", "text/csv", new byte[10 * 1024 * 1024 + 1]);

        MessageCodeException e = catchThrowableOfType(() -> service.upload(PM, 1L, big), MessageCodeException.class);

        assertThat(e.getCode()).isEqualTo("MSG28");
        assertThat(e.getStatus()).isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE);
        verifyNoInteractions(previews, rows);
    }

    @Test
    void rejectsProjectTheCallerDoesNotManage_AC_001_9() {
        MessageCodeException e = catchThrowableOfType(
                () -> service.upload(PM, 2L, csv("tickets.csv", rowsOf("T-1"))), MessageCodeException.class);

        assertThat(e.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(e.getCode()).isEqualTo("MSG08");
        verifyNoInteractions(previews, rows);
    }

    @Test
    void replacesTheCallersPreviousPreview_AC_001_11() {
        service.upload(PM, 1L, csv("tickets.csv", rowsOf("T-1")));

        InOrder order = inOrder(previews);
        order.verify(previews).deleteByOwner(PM);
        order.verify(previews).save(any(ImportPreview.class));
    }

    @Test
    void rejectedFileKeepsThePreviousPreview_AC_001_11() {
        catchThrowableOfType(() -> service.upload(PM, 1L, csv("tickets.csv", HEADER)), MessageCodeException.class);

        verify(previews, never()).deleteByOwner(PM);
    }

    @Test
    void reportsErrorRowCountWithMsg30_AC_002_9() {
        when(tickets.findExistingTicketIds(anyCollection())).thenReturn(Set.of("T-2"));

        ImportPreviewResponse response = service.upload(PM, 1L, csv("tickets.csv", rowsOf("T-1", "T-2", "T-3", "T-3")));

        assertThat(response.validCount()).isEqualTo(1);
        assertThat(response.errorCount()).isEqualTo(3);
        assertThat(response.message()).isEqualTo(new MessageRef("MSG30", Map.of("count", 3L)));
        assertThat(savedRows())
                .extracting(ImportPreviewRow::getStatus)
                .containsExactly(
                        PreviewRowStatus.VALID, PreviewRowStatus.ERROR, PreviewRowStatus.ERROR, PreviewRowStatus.ERROR);
    }

    @Test
    void looksUpExistingTicketIdsOnce_AC_002_5() {
        service.upload(PM, 1L, csv("tickets.csv", rowsOf("T-1", "T-2", "T-1")));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<java.util.Collection<String>> ids = ArgumentCaptor.forClass(java.util.Collection.class);
        verify(tickets).findExistingTicketIds(ids.capture());
        assertThat(ids.getValue()).containsExactly("T-1", "T-2");
    }

    @Test
    void templateIsTheHeaderRowOnly_AC_005_1() {
        assertThat(service.template())
                .isEqualTo("ticket_id,title,description,author,resolved_date,symptoms,root_cause,"
                        + "resolution_steps,workaround,scope\n");
    }

    @Test
    void listCutsSolutionSectionsToTwoHundredCharacters_AC_002_8() {
        String longSymptoms = "😀".repeat(250);
        String body = HEADER + "T-1,Login fails," + longSymptoms + ",Bad token,Restart,All users\n";

        ImportPreviewResponse response = service.upload(PM, 1L, csv("tickets.csv", body));

        String shown = response.rows().getContent().get(0).symptoms();
        assertThat(shown.codePointCount(0, shown.length())).isEqualTo(200);
    }
}
