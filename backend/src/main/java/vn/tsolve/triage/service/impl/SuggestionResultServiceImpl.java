package vn.tsolve.triage.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.UriUtils;
import vn.tsolve.common.exception.NotFoundException;
import vn.tsolve.triage.dto.HistoryQuery;
import vn.tsolve.triage.dto.ManagedProject;
import vn.tsolve.triage.dto.SuggestionProject;
import vn.tsolve.triage.dto.SuggestionResultDetail;
import vn.tsolve.triage.dto.SuggestionResultSummary;
import vn.tsolve.triage.dto.SuggestionWikiSource;
import vn.tsolve.triage.entity.SuggestionResultRecord;
import vn.tsolve.triage.exception.InvalidHistoryFilterException;
import vn.tsolve.triage.exception.ProjectNotManagedException;
import vn.tsolve.triage.mapper.SuggestionResultMapper;
import vn.tsolve.triage.repository.HistoryFilter;
import vn.tsolve.triage.repository.SuggestionResultRecordRepository;
import vn.tsolve.triage.service.JiraSite;
import vn.tsolve.triage.service.ManagedProjects;
import vn.tsolve.triage.service.SuggestionResultService;
import vn.tsolve.triage.service.WikiDocumentAccess;

@Service
public class SuggestionResultServiceImpl implements SuggestionResultService {

    /** Calendar days of the filter are Vietnam days, as on the web app (AC-003.2). */
    static final ZoneId APP_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    static final int DEFAULT_SIZE = 20;
    static final int MAX_SIZE = 100;
    static final String NEWEST_FIRST = "createdAt,desc";
    static final Sort ORDER = Sort.by(Sort.Order.desc("createdAt"), Sort.Order.asc("jiraIssueKey"));

    private static final Set<String> ANSWERS = Set.of("YES", "NO", "NONE");
    private static final Pattern CALENDAR_DATE = Pattern.compile("\\d{4}-\\d{2}-\\d{2}");
    private static final String INVALID_VALUE = "MSG06";
    private static final String INVALID_DATE_RANGE = "MSG39";
    private static final String NO_RESULT = "MSG04";

    private final SuggestionResultRecordRepository repository;
    private final ManagedProjects managedProjects;
    private final JiraSite jiraSite;
    private final WikiDocumentAccess wikiDocuments;
    private final SuggestionResultMapper mapper;
    private final ObjectMapper json;

    public SuggestionResultServiceImpl(
            SuggestionResultRecordRepository repository,
            ManagedProjects managedProjects,
            JiraSite jiraSite,
            WikiDocumentAccess wikiDocuments,
            SuggestionResultMapper mapper,
            ObjectMapper json) {
        this.repository = repository;
        this.managedProjects = managedProjects;
        this.jiraSite = jiraSite;
        this.wikiDocuments = wikiDocuments;
        this.mapper = mapper;
        this.json = json;
    }

    @Override
    @Transactional(readOnly = true)
    public Page<SuggestionResultSummary> history(String user, HistoryQuery query) {
        Pageable pageable = pageable(query);
        String answer = answer(query.answer());
        Range range = range(query.from(), query.to());

        Map<UUID, ManagedProject> managed = managedById(user);
        Set<UUID> projectIds = managed.keySet();
        if (query.projectId() != null) {
            ManagedProject chosen = managed.values().stream()
                    .filter(project -> project.publicId().equals(query.projectId()))
                    .findFirst()
                    .orElseThrow(ProjectNotManagedException::new);
            projectIds = Set.of(UUID.fromString(chosen.publicId()));
        }
        if (projectIds.isEmpty()) {
            return Page.empty(pageable);
        }

        String siteUrl = jiraSite.url().orElse(null);
        return repository
                .search(new HistoryFilter(projectIds, answer, range.start(), range.end()), pageable)
                .map(record -> {
                    SourcesUsed sources = SourcesUsed.parse(json, record.getSourcesUsed());
                    return mapper.toSummary(
                            record,
                            project(managed.get(record.getProjectId())),
                            issueUrl(siteUrl, record.getJiraIssueKey()),
                            sources.ticketCount(),
                            sources.wikiCount());
                });
    }

    @Override
    @Transactional(readOnly = true)
    public SuggestionResultDetail detail(String user, boolean projectManager, String issueKey) {
        Map<UUID, ManagedProject> managed = projectManager ? managedById(user) : Map.of();
        SuggestionResultRecord record = managed.isEmpty()
                ? null
                : repository
                        .findByJiraIssueKeyAndProjectIdIn(issueKey, managed.keySet())
                        .orElse(null);
        if (record == null) {
            throw new NotFoundException(NO_RESULT, Map.of());
        }

        SourcesUsed sources = SourcesUsed.parse(json, record.getSourcesUsed());
        List<String> wikiIds =
                sources.wiki().stream().map(SourcesUsed.WikiRef::id).toList();
        Set<String> available = wikiIds.isEmpty() ? Set.of() : wikiDocuments.available(user, wikiIds);
        List<SuggestionWikiSource> wikiSources = sources.wiki().stream()
                .map(ref -> new SuggestionWikiSource(ref.id(), ref.title(), available.contains(ref.id())))
                .toList();
        return mapper.toDetail(
                record,
                project(managed.get(record.getProjectId())),
                issueUrl(jiraSite.url().orElse(null), record.getJiraIssueKey()),
                sources.ticketCount(),
                wikiSources);
    }

    // suggestion_result.project_id holds the Project's public_id (tracker's 019-tracker-suggestion-uuid-keys).
    private Map<UUID, ManagedProject> managedById(String user) {
        return managedProjects.of(user).stream()
                .collect(Collectors.toMap(
                        project -> UUID.fromString(project.publicId()), Function.identity(), (first, second) -> first));
    }

    private static SuggestionProject project(ManagedProject project) {
        return new SuggestionProject(project.publicId(), project.key());
    }

    // Only an HTTPS site gives a link; the web app checks the scheme again before it renders one.
    static String issueUrl(String siteUrl, String issueKey) {
        if (siteUrl == null || !siteUrl.startsWith("https://")) {
            return null;
        }
        String base = siteUrl.endsWith("/") ? siteUrl.substring(0, siteUrl.length() - 1) : siteUrl;
        return base + "/browse/" + UriUtils.encodePathSegment(issueKey, StandardCharsets.UTF_8);
    }

    private static Pageable pageable(HistoryQuery query) {
        int page = query.page() == null ? 0 : query.page();
        int size = query.size() == null ? DEFAULT_SIZE : query.size();
        if (page < 0 || size < 1 || size > MAX_SIZE) {
            throw new InvalidHistoryFilterException(INVALID_VALUE);
        }
        if (query.sort() != null && !NEWEST_FIRST.equals(query.sort())) {
            throw new InvalidHistoryFilterException(INVALID_VALUE);
        }
        return PageRequest.of(page, size, ORDER);
    }

    private static String answer(String answer) {
        if (answer != null && !ANSWERS.contains(answer)) {
            throw new InvalidHistoryFilterException(INVALID_VALUE);
        }
        return answer;
    }

    /** The half-open {@code [start, end)} of the inclusive Vietnam days; both null without a range. */
    record Range(Instant start, Instant end) {}

    static Range range(String from, String to) {
        if (from == null && to == null) {
            return new Range(null, null);
        }
        if (from == null || to == null) {
            throw new InvalidHistoryFilterException(INVALID_DATE_RANGE);
        }
        // Only yyyy-MM-dd, as the web app sends: a signed or 5+ digit year would overflow the day arithmetic or
        // PostgreSQL's timestamp range instead of getting MSG39 (BUG-106).
        if (!CALENDAR_DATE.matcher(from).matches() || !CALENDAR_DATE.matcher(to).matches()) {
            throw new InvalidHistoryFilterException(INVALID_DATE_RANGE);
        }
        LocalDate first;
        LocalDate last;
        try {
            first = LocalDate.parse(from);
            last = LocalDate.parse(to);
        } catch (DateTimeException notADate) {
            throw new InvalidHistoryFilterException(INVALID_DATE_RANGE);
        }
        if (first.isAfter(last)) {
            throw new InvalidHistoryFilterException(INVALID_DATE_RANGE);
        }
        return new Range(
                first.atStartOfDay(APP_ZONE).toInstant(),
                last.plusDays(1).atStartOfDay(APP_ZONE).toInstant());
    }
}
