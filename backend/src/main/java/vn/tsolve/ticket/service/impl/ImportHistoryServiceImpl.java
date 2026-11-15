package vn.tsolve.ticket.service.impl;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.tsolve.common.exception.FieldMessageCodeException;
import vn.tsolve.common.exception.ProblemDetails;
import vn.tsolve.common.security.TSolveUser;
import vn.tsolve.ticket.dto.ImportHistoryItem;
import vn.tsolve.ticket.dto.ImportHistoryQuery;
import vn.tsolve.ticket.entity.ImportBatchStatus;
import vn.tsolve.ticket.entity.TicketSource;
import vn.tsolve.ticket.exception.InvalidPullRangeException;
import vn.tsolve.ticket.repository.ImportBatchRepository;
import vn.tsolve.ticket.repository.ImportBatchRepository.HistoryRow;
import vn.tsolve.ticket.service.ImportHistoryService;

@Service
public class ImportHistoryServiceImpl implements ImportHistoryService {
    private static final Pattern MSG_CODE = Pattern.compile("MSG\\w+");
    private final ImportBatchRepository batches;
    private final Clock clock;

    public ImportHistoryServiceImpl(ImportBatchRepository batches, Clock clock) {
        this.batches = batches;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ImportHistoryItem> list(TSolveUser user, ImportHistoryQuery query) {
        if (query.dateFrom() != null
                && query.dateTo() != null
                && query.dateFrom().isAfter(query.dateTo())) {
            throw InvalidPullRangeException.invalid();
        }
        int page = query.page() == null ? 0 : query.page();
        int size = query.size() == null ? 20 : query.size();
        if (page < 0 || size < 1 || size > 100) {
            throw new FieldMessageCodeException(
                    HttpStatus.BAD_REQUEST, ProblemDetails.UNEXPECTED_ERROR, Map.of(), page < 0 ? "page" : "size");
        }
        Instant from = query.dateFrom() == null
                ? null
                : query.dateFrom().atStartOfDay(clock.getZone()).toInstant();
        Instant to = query.dateTo() == null
                ? null
                : query.dateTo().plusDays(1).atStartOfDay(clock.getZone()).toInstant();
        return batches.findHistory(
                        user.id(),
                        query.projectId(),
                        query.source() == null ? null : query.source().name(),
                        from,
                        to,
                        PageRequest.of(page, size))
                .map(ImportHistoryServiceImpl::item);
    }

    private static ImportHistoryItem item(HistoryRow row) {
        return new ImportHistoryItem(
                row.getBatchId(),
                row.getCreatedAt(),
                row.getPerformedBy(),
                new ImportHistoryItem.Project(row.getProjectId(), row.getProjectKey()),
                new ImportHistoryItem.Department(row.getDepartmentId(), row.getDepartmentName()),
                TicketSource.valueOf(row.getSource()),
                row.getDateFrom(),
                row.getDateTo(),
                row.getFileName(),
                row.getPublishedCount(),
                row.getRemovedCount(),
                row.getErrorCount(),
                ImportBatchStatus.valueOf(row.getStatus()),
                errorCode(row.getError()));
    }

    private static String errorCode(String error) {
        if (error == null) return null;
        Matcher match = MSG_CODE.matcher(error);
        return match.find() ? match.group() : null;
    }
}
