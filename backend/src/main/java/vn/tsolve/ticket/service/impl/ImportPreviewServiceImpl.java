package vn.tsolve.ticket.service.impl;

import java.util.Collection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PagedModel;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.tsolve.common.error.NotFoundException;
import vn.tsolve.ticket.dto.ImportPreviewResponse;
import vn.tsolve.ticket.dto.ImportPreviewRowResponse;
import vn.tsolve.ticket.dto.ImportPreviewRowSummary;
import vn.tsolve.ticket.entity.ImportPreview;
import vn.tsolve.ticket.entity.ImportPreviewRow;
import vn.tsolve.ticket.entity.PreviewRowStatus;
import vn.tsolve.ticket.mapper.ImportPreviewMapper;
import vn.tsolve.ticket.repository.ImportPreviewRepository;
import vn.tsolve.ticket.repository.ImportPreviewRowRepository;
import vn.tsolve.ticket.service.ImportPreviewService;

@Service
public class ImportPreviewServiceImpl implements ImportPreviewService {

    static final int MAX_PAGE_SIZE = 100;

    private final ImportPreviewRepository previews;
    private final ImportPreviewRowRepository previewRows;
    private final ImportPreviewMapper mapper;

    public ImportPreviewServiceImpl(
            ImportPreviewRepository previews, ImportPreviewRowRepository previewRows, ImportPreviewMapper mapper) {
        this.previews = previews;
        this.previewRows = previewRows;
        this.mapper = mapper;
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
        ImportPreview preview = owned(user, previewId);
        previewRows
                .findAllById(rowNumbers.stream()
                        .map(n -> new ImportPreviewRow.Id(preview.getId(), n))
                        .toList())
                .forEach(ImportPreviewRow::remove);
        previewRows.flush();
        return ImportPreviewResponse.of(
                preview.getId(),
                preview.getProjectId(),
                preview.getFileName(),
                previewRows.countByIdPreviewIdAndStatus(preview.getId(), PreviewRowStatus.VALID),
                previewRows.countByIdPreviewIdAndStatus(preview.getId(), PreviewRowStatus.ERROR),
                previewRows.countByIdPreviewIdAndStatus(preview.getId(), PreviewRowStatus.REMOVED),
                page(preview.getId(), null, PageRequest.of(0, CsvImportServiceImpl.FIRST_PAGE_SIZE)));
    }

    private ImportPreview owned(String user, long previewId) {
        // another user's preview is "not found", so ids cannot be probed (design: Cross-user access)
        return previews.findByIdAndOwner(previewId, user)
                .orElseThrow(() -> new NotFoundException("Import preview " + previewId));
    }

    private Page<ImportPreviewRowSummary> page(long previewId, PreviewRowStatus status, Pageable requested) {
        Pageable page = PageRequest.of(requested.getPageNumber(), Math.min(requested.getPageSize(), MAX_PAGE_SIZE));
        Page<ImportPreviewRow> rows = status == null
                ? previewRows.findByIdPreviewIdOrderByIdRowNo(previewId, page)
                : previewRows.findByIdPreviewIdAndStatusOrderByIdRowNo(previewId, status, page);
        return rows.map(mapper::toSummary);
    }
}
