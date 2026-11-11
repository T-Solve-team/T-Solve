package vn.tsolve.ticket.service.impl;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import vn.tsolve.common.exception.NotFoundException;
import vn.tsolve.ticket.dto.ImportPreviewResponse;
import vn.tsolve.ticket.dto.ImportPreviewRowSummary;
import vn.tsolve.ticket.entity.ImportPreview;
import vn.tsolve.ticket.entity.ImportPreviewRow;
import vn.tsolve.ticket.exception.InvalidPullRangeException;
import vn.tsolve.ticket.exception.JiraUnavailableException;
import vn.tsolve.ticket.exception.ProjectAccessDeniedException;
import vn.tsolve.ticket.mapper.ImportPreviewMapper;
import vn.tsolve.ticket.repository.ImportPreviewRepository;
import vn.tsolve.ticket.repository.ImportPreviewRowRepository;
import vn.tsolve.ticket.repository.TicketRepository;
import vn.tsolve.ticket.service.JiraImportService;
import vn.tsolve.ticket.service.JiraIssue;
import vn.tsolve.ticket.service.JiraIssuePage;
import vn.tsolve.ticket.service.JiraIssueSource;
import vn.tsolve.ticket.service.ProjectAccess;

@Service
public class JiraImportServiceImpl implements JiraImportService {

    static final int MAX_ISSUES = 100;
    /** At most 20 Jira pages (2,000 issues) per pull, so a range of mostly imported issues stays fast (Edge 3). */
    static final int MAX_PAGES = 20;
    /** Every Jira call of one pull, retries included, together (AC-001.13). */
    static final Duration PULL_BUDGET = Duration.ofSeconds(60);

    private static final Logger log = LoggerFactory.getLogger(JiraImportServiceImpl.class);
    private static final ZoneId VIETNAM = ZoneId.of("Asia/Ho_Chi_Minh");

    private final JiraIssueSource jira;
    private final ImportPreviewRepository previews;
    private final ImportPreviewRowRepository previewRows;
    private final TicketRepository tickets;
    private final ProjectAccess projects;
    private final ImportBatchRecorder recorder;
    private final ImportPreviewMapper mapper;
    private final Clock clock;
    private final TransactionTemplate transaction;

    public JiraImportServiceImpl(
            JiraIssueSource jira,
            ImportPreviewRepository previews,
            ImportPreviewRowRepository previewRows,
            TicketRepository tickets,
            ProjectAccess projects,
            ImportBatchRecorder recorder,
            ImportPreviewMapper mapper,
            Clock clock,
            PlatformTransactionManager transactionManager) {
        this.jira = jira;
        this.previews = previews;
        this.previewRows = previewRows;
        this.tickets = tickets;
        this.projects = projects;
        this.recorder = recorder;
        this.mapper = mapper;
        this.clock = clock;
        this.transaction = new TransactionTemplate(transactionManager);
    }

    @Override
    public ImportPreviewResponse pull(String user, UUID projectId, LocalDate from, LocalDate to) {
        // order (design Pull): dates, then Project access, then Jira — a rejected pull never calls Jira
        if (from == null) {
            throw InvalidPullRangeException.missing("resolvedFrom");
        }
        if (to == null) {
            throw InvalidPullRangeException.missing("resolvedTo");
        }
        LocalDate today = LocalDate.now(clock.withZone(VIETNAM));
        if (from.isAfter(to) || to.isAfter(today)) {
            throw InvalidPullRangeException.invalid();
        }
        if (!projects.managesActive(user, projectId)) {
            throw new ProjectAccessDeniedException();
        }

        Pulled pulled = read(user, projectId, from, to);
        if (pulled.issues().isEmpty()) {
            throw new NotFoundException("MSG04", Map.of());
        }
        // the Jira calls are done; only the database writes share a transaction (AGENTS.md §4)
        try {
            return transaction.execute(status -> save(user, projectId, from, to, pulled));
        } catch (DataIntegrityViolationException e) {
            // a parallel pull of this user saved its open preview first (uq_import_preview_owner_open): saving again
            // deletes it, so the later pull wins as AC-001.10 asks (BUG-193)
            log.info("Jira pull of project {} met a parallel pull of the same user; saving again", projectId);
            return transaction.execute(status -> save(user, projectId, from, to, pulled));
        }
    }

    private record Pulled(List<JiraIssue> issues, boolean moreAvailable) {}

    private Pulled read(String user, UUID projectId, LocalDate from, LocalDate to) {
        String key = projects.jiraProjectKey(projectId).orElse(null);
        try {
            if (key == null) {
                throw new JiraUnavailableException("not connected");
            }
            Instant deadline = Instant.now(clock).plus(PULL_BUDGET);
            List<JiraIssue> kept = new ArrayList<>();
            String token = null;
            int pages = 0;
            do {
                JiraIssuePage page = jira.resolvedIssues(key, from, to, token, deadline);
                pages++;
                Set<String> taken = tickets.findExistingTicketIds(
                        page.issues().stream().map(JiraIssue::key).toList());
                for (JiraIssue issue : page.issues()) {
                    if (taken.contains(issue.key())) {
                        continue;
                    }
                    if (kept.size() == MAX_ISSUES) {
                        return new Pulled(kept, true);
                    }
                    kept.add(issue);
                }
                token = page.nextPageToken();
            } while (token != null && pages < MAX_PAGES);
            // stopped at the scan cap with pages left: more issues may match (design: Scan cap)
            return new Pulled(kept, token != null);
        } catch (JiraUnavailableException e) {
            log.warn("Jira pull failed project={} jiraKey={} reason={}", projectId, key, e.getReason());
            try {
                recorder.recordJiraFailed(projectId, user, JiraUnavailableException.class.getSimpleName() + " MSG50");
            } catch (RuntimeException recordError) {
                // never let a recording problem hide the MSG50 answer (BUG-192)
                log.error(
                        "Could not record the Failed Jira Import Batch of project {}: {}",
                        projectId,
                        recordError.getClass().getSimpleName());
            }
            throw e;
        }
    }

    private ImportPreviewResponse save(String user, UUID projectId, LocalDate from, LocalDate to, Pulled pulled) {
        previews.deleteByOwner(user);
        ImportPreview preview = previews.save(
                ImportPreview.jira(user, projectId, from, to, pulled.moreAvailable(), Instant.now(clock)));
        List<ImportPreviewRow> rows = new ArrayList<>(pulled.issues().size());
        for (JiraIssue issue : pulled.issues()) {
            rows.add(ImportPreviewRow.jiraIssue(
                    preview.getId(), rows.size() + 1, values(issue), issue.url(), issue.comments()));
        }
        previewRows.saveAll(rows);

        List<ImportPreviewRowSummary> firstPage =
                rows.subList(0, Math.min(CsvImportServiceImpl.FIRST_PAGE_SIZE, rows.size())).stream()
                        .map(mapper::toSummary)
                        .toList();
        return ImportPreviewResponse.of(
                preview,
                0,
                0,
                0,
                rows.size(),
                new PageImpl<>(firstPage, PageRequest.of(0, CsvImportServiceImpl.FIRST_PAGE_SIZE), rows.size()));
    }

    private static ImportPreviewRow.Values values(JiraIssue issue) {
        String resolved = issue.resolvedAt().atZone(VIETNAM).toLocalDate().toString();
        return new ImportPreviewRow.Values(
                issue.key(),
                issue.summary(),
                issue.description(),
                issue.assignee(),
                resolved,
                null,
                null,
                null,
                null,
                null);
    }
}
