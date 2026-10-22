package vn.tsolve.ticket.service.impl;

import java.io.IOException;
import java.io.InputStream;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import vn.tsolve.ticket.csv.CsvRowValidator;
import vn.tsolve.ticket.csv.CsvTicketParser;
import vn.tsolve.ticket.csv.ParsedCsv;
import vn.tsolve.ticket.dto.ImportPreviewResponse;
import vn.tsolve.ticket.dto.ImportPreviewRowSummary;
import vn.tsolve.ticket.entity.ImportPreview;
import vn.tsolve.ticket.entity.ImportPreviewRow;
import vn.tsolve.ticket.entity.TicketSource;
import vn.tsolve.ticket.exception.CsvFileRejectedException;
import vn.tsolve.ticket.exception.ProjectAccessDeniedException;
import vn.tsolve.ticket.exception.UploadTooLargeException;
import vn.tsolve.ticket.mapper.ImportPreviewMapper;
import vn.tsolve.ticket.repository.ImportPreviewRepository;
import vn.tsolve.ticket.repository.ImportPreviewRowRepository;
import vn.tsolve.ticket.repository.TicketRepository;
import vn.tsolve.ticket.service.CsvImportService;
import vn.tsolve.ticket.service.ProjectAccess;

@Service
public class CsvImportServiceImpl implements CsvImportService {

    static final long MAX_IMPORT_SIZE = 10L * 1024 * 1024;
    static final int FIRST_PAGE_SIZE = 50;

    private final CsvTicketParser parser;
    private final CsvRowValidator validator;
    private final ImportPreviewRepository previews;
    private final ImportPreviewRowRepository previewRows;
    private final TicketRepository tickets;
    private final ProjectAccess projects;
    private final ImportPreviewMapper mapper;
    private final Clock clock;

    public CsvImportServiceImpl(
            CsvTicketParser parser,
            CsvRowValidator validator,
            ImportPreviewRepository previews,
            ImportPreviewRowRepository previewRows,
            TicketRepository tickets,
            ProjectAccess projects,
            ImportPreviewMapper mapper,
            Clock clock) {
        this.parser = parser;
        this.validator = validator;
        this.previews = previews;
        this.previewRows = previewRows;
        this.tickets = tickets;
        this.projects = projects;
        this.mapper = mapper;
        this.clock = clock;
    }

    @Override
    @Transactional
    public ImportPreviewResponse upload(String user, long projectId, MultipartFile file) {
        // order (design B6): file type and size, then Project access, then content
        String fileName = file.getOriginalFilename();
        if (fileName == null || !fileName.toLowerCase(Locale.ROOT).endsWith(".csv")) {
            throw new CsvFileRejectedException("MSG27");
        }
        if (file.getSize() > MAX_IMPORT_SIZE) {
            throw new UploadTooLargeException();
        }
        if (!projects.managesActive(user, projectId)) {
            throw new ProjectAccessDeniedException();
        }

        ParsedCsv csv = parse(file);
        List<CsvRowValidator.Result> checked =
                validator.validate(csv.rows(), tickets.findExistingTicketIds(CsvRowValidator.ticketIdsOf(csv.rows())));

        previews.deleteByOwner(user);
        ImportPreview preview =
                previews.save(new ImportPreview(user, projectId, TicketSource.CSV, fileName, Instant.now(clock)));
        List<ImportPreviewRow> rows = new ArrayList<>(checked.size());
        for (CsvRowValidator.Result r : checked) {
            rows.add(new ImportPreviewRow(
                    preview.getId(), r.row().rowNo(), r.row().values(), r.errors()));
        }
        previewRows.saveAll(rows);

        long valid = rows.stream().filter(ImportPreviewRow::isValid).count();
        List<ImportPreviewRowSummary> firstPage = rows.subList(0, Math.min(FIRST_PAGE_SIZE, rows.size())).stream()
                .map(mapper::toSummary)
                .toList();
        return ImportPreviewResponse.of(
                preview.getId(),
                projectId,
                fileName,
                valid,
                rows.size() - valid,
                0,
                new PageImpl<>(firstPage, PageRequest.of(0, FIRST_PAGE_SIZE), rows.size()));
    }

    private ParsedCsv parse(MultipartFile file) {
        try (InputStream in = file.getInputStream()) {
            return parser.parse(in);
        } catch (IOException e) {
            throw new CsvFileRejectedException("MSG27");
        }
    }
}
