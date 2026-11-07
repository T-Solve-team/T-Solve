package vn.tsolve.ticket.service.impl;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PagedModel;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.tsolve.common.exception.BusinessRuleException;
import vn.tsolve.common.exception.NotFoundException;
import vn.tsolve.ticket.csv.CsvRowValidator;
import vn.tsolve.ticket.dto.FillSolutionRequest;
import vn.tsolve.ticket.dto.ImportPreviewResponse;
import vn.tsolve.ticket.dto.ImportPreviewRowResponse;
import vn.tsolve.ticket.dto.ImportPreviewRowSummary;
import vn.tsolve.ticket.entity.ImportPreview;
import vn.tsolve.ticket.entity.ImportPreviewRow;
import vn.tsolve.ticket.entity.ImportType;
import vn.tsolve.ticket.entity.PreviewRowStatus;
import vn.tsolve.ticket.exception.ProjectAccessDeniedException;
import vn.tsolve.ticket.exception.SolutionTooLongException;
import vn.tsolve.ticket.mapper.ImportPreviewMapper;
import vn.tsolve.ticket.repository.ImportPreviewRepository;
import vn.tsolve.ticket.repository.ImportPreviewRowRepository;
import vn.tsolve.ticket.service.ImportPreviewService;
import vn.tsolve.ticket.service.ProjectAccess;

@Service
public class ImportPreviewServiceImpl implements ImportPreviewService {

    static final int MAX_PAGE_SIZE = 100;

    private final ImportPreviewRepository previews;
    private final ImportPreviewRowRepository previewRows;
    private final ImportPreviewMapper mapper;
    private final ProjectAccess projects;

    public ImportPreviewServiceImpl(
            ImportPreviewRepository previews,
            ImportPreviewRowRepository previewRows,
            ImportPreviewMapper mapper,
            ProjectAccess projects) {
        this.previews = previews;
        this.previewRows = previewRows;
        this.mapper = mapper;
        this.projects = projects;
    }

    @Override
    @Transactional(readOnly = true)
    public ImportPreviewResponse preview(String user, long previewId) {
        return summary(owned(user, previewId));
    }

    @Override
    @Transactional(readOnly = true)
    public PagedModel<ImportPreviewRowSummary> rows(
            String user, long previewId, PreviewRowStatus status, Pageable page) {
        ImportPreview preview = owned(user, previewId);
        return new PagedModel<>(page(preview.getId(), status, page));
    }

    @Override
    @Transactional(readOnly = true)
    public ImportPreviewRowResponse row(String user, long previewId, int rowNo) {
        ImportPreview preview = owned(user, previewId);
        return previewRows
                .findById(new ImportPreviewRow.Id(preview.getId(), rowNo))
                .map(mapper::toResponse)
                .orElseThrow(() -> new NotFoundException("Row " + rowNo + " of import preview " + previewId));
    }

    @Override
    @Transactional
    public ImportPreviewResponse removeRows(String user, long previewId, Collection<Integer> rowNumbers) {
        owned(user, previewId);
        // the lock Fill solution and Confirm take, so a removed row cannot be filled or imported meanwhile (BUG-152)
        ImportPreview preview = previews.lockByIdAndOwner(previewId, user)
                .orElseThrow(() -> new NotFoundException("Import preview " + previewId));
        previewRows
                .findAllById(rowNumbers.stream()
                        .map(n -> new ImportPreviewRow.Id(preview.getId(), n))
                        .toList())
                .forEach(ImportPreviewRow::remove);
        previewRows.flush();
        return summary(preview);
    }

    @Override
    @Transactional
    public ImportPreviewResponse fillSolution(String user, long previewId, FillSolutionRequest solution) {
        // the request has stripped its sections already (BUG-151)
        String symptoms = solution.symptoms();
        String rootCause = solution.rootCause();
        String resolutionSteps = solution.resolutionSteps();
        String workaround = solution.workaround();
        String scope = solution.scope();
        int length = Stream.of(symptoms, rootCause, resolutionSteps, workaround, scope)
                .filter(Objects::nonNull)
                .mapToInt(s -> s.codePointCount(0, s.length()))
                .sum();
        if (length > CsvRowValidator.SOLUTION_MAX_LENGTH) {
            throw new SolutionTooLongException(CsvRowValidator.SOLUTION_MAX_LENGTH);
        }

        owned(user, previewId);
        // the lock Confirm takes: a running Confirm finishes first, then the preview is gone (AC-013.4)
        ImportPreview preview = previews.lockByIdAndOwner(previewId, user)
                .orElseThrow(() -> new NotFoundException("Import preview " + previewId));
        if (preview.getImportType() != ImportType.WITHOUT_SOLUTION) {
            throw new BusinessRuleException("MSG06", Map.of());
        }
        Set<Integer> numbers = new LinkedHashSet<>(solution.rowNumbers());
        List<ImportPreviewRow> rows = previewRows.findAllById(
                numbers.stream().map(n -> new ImportPreviewRow.Id(previewId, n)).toList());
        if (rows.size() < numbers.size()) {
            throw new NotFoundException("A row of import preview " + previewId);
        }
        if (rows.stream()
                .anyMatch(r -> r.getStatus() == PreviewRowStatus.ERROR || r.getStatus() == PreviewRowStatus.REMOVED)) {
            throw new BusinessRuleException("MSG06", Map.of());
        }
        rows.forEach(r -> r.fillSolution(symptoms, rootCause, resolutionSteps, workaround, scope));
        previewRows.flush();
        return summary(preview);
    }

    /** The caller's own preview of a Project they still manage (AC-003.2, AC-003.4). */
    private ImportPreview owned(String user, long previewId) {
        // another user's preview is "not found", so ids cannot be probed (design: Cross-user access)
        ImportPreview preview = previews.findByIdAndOwner(previewId, user)
                .orElseThrow(() -> new NotFoundException("Import preview " + previewId));
        if (!projects.managesActive(user, preview.getProjectId())) {
            throw new ProjectAccessDeniedException();
        }
        return preview;
    }

    private ImportPreviewResponse summary(ImportPreview preview) {
        return ImportPreviewResponse.of(
                preview.getId(),
                preview.getProjectId(),
                preview.getFileName(),
                preview.getImportType(),
                previewRows.countByIdPreviewIdAndStatus(preview.getId(), PreviewRowStatus.VALID),
                previewRows.countByIdPreviewIdAndStatus(preview.getId(), PreviewRowStatus.ERROR),
                previewRows.countByIdPreviewIdAndStatus(preview.getId(), PreviewRowStatus.REMOVED),
                previewRows.countByIdPreviewIdAndStatus(preview.getId(), PreviewRowStatus.NEEDS_SOLUTION),
                page(preview.getId(), null, PageRequest.of(0, CsvImportServiceImpl.FIRST_PAGE_SIZE)));
    }

    private Page<ImportPreviewRowSummary> page(long previewId, PreviewRowStatus status, Pageable requested) {
        Pageable page = PageRequest.of(requested.getPageNumber(), Math.min(requested.getPageSize(), MAX_PAGE_SIZE));
        Page<ImportPreviewRow> rows = status == null
                ? previewRows.findByIdPreviewIdOrderByIdRowNo(previewId, page)
                : previewRows.findByIdPreviewIdAndStatusOrderByIdRowNo(previewId, status, page);
        return rows.map(mapper::toSummary);
    }
}
