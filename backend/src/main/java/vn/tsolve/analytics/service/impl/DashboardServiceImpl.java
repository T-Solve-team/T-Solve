package vn.tsolve.analytics.service.impl;

import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.tsolve.analytics.dto.KnowledgeDashboardResponse;
import vn.tsolve.analytics.dto.KnowledgeDashboardResponse.Coverage;
import vn.tsolve.analytics.dto.KnowledgeDashboardResponse.DayOutcome;
import vn.tsolve.analytics.dto.KnowledgeDashboardResponse.DepartmentCoverage;
import vn.tsolve.analytics.dto.KnowledgeDashboardResponse.Feedback;
import vn.tsolve.analytics.dto.KnowledgeDashboardResponse.Gaps;
import vn.tsolve.analytics.dto.KnowledgeDashboardResponse.Outcomes;
import vn.tsolve.analytics.dto.KnowledgeDashboardResponse.Period;
import vn.tsolve.analytics.dto.KnowledgeDashboardResponse.ProjectOutcome;
import vn.tsolve.analytics.dto.KnowledgeDashboardResponse.Suggestions;
import vn.tsolve.analytics.repository.DashboardRepository;
import vn.tsolve.analytics.repository.DashboardRepository.OutcomeCount;
import vn.tsolve.analytics.repository.DashboardRepository.TicketStatusCount;
import vn.tsolve.analytics.service.DashboardService;
import vn.tsolve.common.security.Role;
import vn.tsolve.common.security.TSolveUser;

@Service
@Transactional(readOnly = true)
public class DashboardServiceImpl implements DashboardService {
    private final DashboardRepository repository;
    private final Clock clock;

    public DashboardServiceImpl(DashboardRepository repository, ObjectProvider<Clock> clocks) {
        this.repository = repository;
        this.clock = clocks.getIfAvailable(Clock::systemUTC);
    }

    @Override
    public KnowledgeDashboardResponse dashboard(
            TSolveUser user, String from, String to, String departmentId, String projectId) {
        DashboardPeriod period = DashboardPeriod.parse(from, to, clock);
        DashboardScope scope = DashboardScope.resolve(user, departmentId, projectId, repository);
        Long ownDepartment = user.roles().contains(Role.ADMIN)
                ? null
                : scope.departments().iterator().next();
        var options = repository.findOptions(ownDepartment);
        var outcomeCounts =
                repository.findOutcomeCounts(scope.departments(), scope.projectId(), period.start(), period.end());
        var statusCounts = repository.findTicketStatusCounts(scope.departments(), scope.projectId());
        return new KnowledgeDashboardResponse(
                new Period(period.from(), period.to()),
                options,
                outcomes(period, outcomeCounts, statusCounts),
                coverage(statusCounts),
                new Feedback(0, 0, null, List.of(), List.of()),
                new Gaps(List.of(), 0, List.of()),
                new Suggestions(0, 0, null));
    }

    private Outcomes outcomes(DashboardPeriod period, List<OutcomeCount> counts, List<TicketStatusCount> statuses) {
        var byDay = counts.stream().collect(Collectors.groupingBy(OutcomeCount::date));
        var perDay = period.days().stream()
                .map(day -> {
                    var totals = outcomeTotals(byDay.getOrDefault(day, List.of()));
                    return new DayOutcome(day, totals.approved(), totals.rejected(), totals.changesRequested());
                })
                .toList();
        // Preserve the query's case-insensitive name order and primary-key tie-breaker.
        var byProject = counts.stream()
                .collect(Collectors.groupingBy(OutcomeCount::projectId, LinkedHashMap::new, Collectors.toList()));
        var perProject = byProject.values().stream()
                .map(rows -> {
                    var project = rows.getFirst();
                    var totals = outcomeTotals(rows);
                    return new ProjectOutcome(
                            project.projectId(),
                            project.name(),
                            totals.approved(),
                            totals.rejected(),
                            totals.changesRequested());
                })
                .toList();
        var totals = outcomeTotals(counts);
        return new Outcomes(
                totals.approved(),
                totals.rejected(),
                totals.changesRequested(),
                share(totals.approved(), totals.approved() + totals.rejected() + totals.changesRequested()),
                countStatus(statuses, "PENDING_REVIEW"),
                countStatus(statuses, "EXPIRED"),
                perDay,
                perProject);
    }

    private OutcomeTotals outcomeTotals(List<OutcomeCount> counts) {
        return new OutcomeTotals(
                counts.stream().mapToLong(OutcomeCount::approved).sum(),
                counts.stream().mapToLong(OutcomeCount::rejected).sum(),
                counts.stream().mapToLong(OutcomeCount::changesRequested).sum());
    }

    private Coverage coverage(List<TicketStatusCount> counts) {
        var byDepartment = counts.stream()
                .collect(Collectors.groupingBy(
                        TicketStatusCount::departmentId, LinkedHashMap::new, Collectors.toList()));
        var perDepartment = byDepartment.values().stream()
                .map(rows -> {
                    var department = rows.getFirst();
                    long published = countStatus(rows, "PUBLISHED");
                    long all = rows.stream().mapToLong(TicketStatusCount::count).sum();
                    return new DepartmentCoverage(
                            department.departmentId(), department.name(), published, all, share(published, all));
                })
                .toList();
        long published = perDepartment.stream()
                .mapToLong(DepartmentCoverage::publishedTickets)
                .sum();
        long all =
                perDepartment.stream().mapToLong(DepartmentCoverage::allTickets).sum();
        return new Coverage(published, all, share(published, all), perDepartment);
    }

    private long countStatus(List<TicketStatusCount> counts, String status) {
        return counts.stream()
                .filter(row -> row.status().equals(status))
                .mapToLong(TicketStatusCount::count)
                .sum();
    }

    private Double share(long numerator, long denominator) {
        return denominator == 0 ? null : (double) numerator / denominator;
    }

    private record OutcomeTotals(long approved, long rejected, long changesRequested) {}
}
