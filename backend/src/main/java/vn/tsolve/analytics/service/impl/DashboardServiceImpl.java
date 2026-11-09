package vn.tsolve.analytics.service.impl;

import java.time.Clock;
import java.util.List;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.tsolve.analytics.dto.KnowledgeDashboardResponse;
import vn.tsolve.analytics.dto.KnowledgeDashboardResponse.Coverage;
import vn.tsolve.analytics.dto.KnowledgeDashboardResponse.DayOutcome;
import vn.tsolve.analytics.dto.KnowledgeDashboardResponse.Feedback;
import vn.tsolve.analytics.dto.KnowledgeDashboardResponse.Gaps;
import vn.tsolve.analytics.dto.KnowledgeDashboardResponse.Outcomes;
import vn.tsolve.analytics.dto.KnowledgeDashboardResponse.Period;
import vn.tsolve.analytics.dto.KnowledgeDashboardResponse.Suggestions;
import vn.tsolve.analytics.repository.DashboardRepository;
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
        var perDay =
                period.days().stream().map(day -> new DayOutcome(day, 0, 0, 0)).toList();
        return new KnowledgeDashboardResponse(
                new Period(period.from(), period.to()),
                options,
                new Outcomes(0, 0, 0, null, 0, 0, perDay, List.of()),
                new Coverage(0, 0, null, List.of()),
                new Feedback(0, 0, null, List.of(), List.of()),
                new Gaps(List.of(), 0, List.of()),
                new Suggestions(0, 0, null));
    }
}
