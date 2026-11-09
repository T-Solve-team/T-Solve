package vn.tsolve.analytics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.security.access.AccessDeniedException;
import vn.tsolve.analytics.dto.KnowledgeDashboardResponse;
import vn.tsolve.analytics.exception.InvalidDashboardFilterException;
import vn.tsolve.analytics.repository.DashboardRepository;
import vn.tsolve.analytics.repository.DashboardRepository.Department;
import vn.tsolve.analytics.repository.DashboardRepository.Project;
import vn.tsolve.analytics.service.impl.DashboardPeriod;
import vn.tsolve.analytics.service.impl.DashboardScope;
import vn.tsolve.analytics.service.impl.DashboardServiceImpl;
import vn.tsolve.common.exception.NotFoundException;
import vn.tsolve.common.security.Role;
import vn.tsolve.common.security.TSolveUser;

class DashboardServiceImplTest {
    private static final UUID DEPARTMENT = UUID.fromString("00000000-0000-0000-0000-000000000011");
    private static final UUID PROJECT = UUID.fromString("00000000-0000-0000-0000-000000000021");
    private static final TSolveUser ADMIN = user(Role.ADMIN);
    private static final TSolveUser PM = user(Role.PROJECT_MANAGER);
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-06T18:00:00Z"), ZoneOffset.UTC);
    private final DashboardRepository repository = mock(DashboardRepository.class);
    private DashboardServiceImpl service;

    @BeforeEach
    void setUp() {
        var beans = new StaticListableBeanFactory();
        beans.addBean("clock", CLOCK);
        service = new DashboardServiceImpl(repository, beans.getBeanProvider(Clock.class));
    }

    @Test
    void defaultsToThirtyVietnamDays_AC_004_2() {
        var result = service.dashboard(ADMIN, null, null, null, null);
        assertThat(result.period().from()).isEqualTo(LocalDate.of(2026, 10, 9));
        assertThat(result.period().to()).isEqualTo(LocalDate.of(2026, 11, 7));
        assertThat(result.outcomes().perDay()).hasSize(30);
        assertThat(result.outcomes().perDay())
                .extracting(KnowledgeDashboardResponse.DayOutcome::date)
                .containsExactlyElementsOf(LocalDate.of(2026, 10, 9)
                        .datesUntil(LocalDate.of(2026, 11, 8))
                        .toList());
    }

    @Test
    void usesUtcClockWhenNoBeanExists_AC_004_2() {
        var withoutClock =
                new DashboardServiceImpl(repository, new StaticListableBeanFactory().getBeanProvider(Clock.class));
        var before = LocalDate.now(Clock.system(java.time.ZoneId.of("Asia/Ho_Chi_Minh")));
        var result = withoutClock.dashboard(ADMIN, null, null, null, null);
        var after = LocalDate.now(Clock.system(java.time.ZoneId.of("Asia/Ho_Chi_Minh")));
        assertThat(result.period().to()).isBetween(before, after);
        assertThat(result.period().from()).isEqualTo(result.period().to().minusDays(29));
    }

    @ParameterizedTest
    @CsvSource(
            value = {
                "NULL,2026-11-07",
                "2026-11-07,NULL",
                "2026-02-30,2026-11-07",
                "2026-1-01,2026-11-07",
                "bad,2026-11-07",
                "2026-11-07,2026-11-06",
                "2026-11-07,2026-11-08",
                "'',2026-11-07",
                "2026-11-07T00:00:00Z,2026-11-07",
                "2026-01-01,2026-02-29",
                "2026-11-07,''",
                "2026-11-08,2026-11-08",
                "2025-11-06,2026-11-07",
                "2026-11-07,2026-11-7",
                "2026-11-07, 2026-11-07T00:00"
            },
            nullValues = "NULL")
    void rejectsInvalidPeriodBeforeIdsOrQueries_AC_004_3(String from, String to) {
        assertThatThrownBy(() -> service.dashboard(PM, from, to, "bad", "bad"))
                .isInstanceOfSatisfying(InvalidDashboardFilterException.class, e -> assertThat(e.getCode())
                        .isEqualTo("MSG39"));
        verifyNoInteractions(repository);
    }

    @Test
    void acceptsExactly366DaysIncludingLeapDay_AC_004_3() {
        var result = service.dashboard(ADMIN, "2024-01-01", "2024-12-31", null, null);
        assertThat(result.outcomes().perDay()).hasSize(366);
        assertThat(result.outcomes().perDay())
                .extracting(KnowledgeDashboardResponse.DayOutcome::date)
                .containsExactlyElementsOf(LocalDate.of(2024, 1, 1)
                        .datesUntil(LocalDate.of(2025, 1, 1))
                        .toList());
    }

    @Test
    void rejects367Days_AC_004_3() {
        assertThatThrownBy(() -> service.dashboard(ADMIN, "2024-01-01", "2025-01-01", null, null))
                .isInstanceOfSatisfying(InvalidDashboardFilterException.class, e -> assertThat(e.getCode())
                        .isEqualTo("MSG39"));
        verifyNoInteractions(repository);
    }

    @Test
    void boundsIncludeWholeVietnamDay_AC_004_3() {
        var period = DashboardPeriod.parse("2026-11-07", "2026-11-07", CLOCK);
        assertThat(period.start()).isEqualTo(Instant.parse("2026-11-06T17:00:00Z"));
        assertThat(period.end()).isEqualTo(Instant.parse("2026-11-07T17:00:00Z"));
        assertThat(period.days()).containsExactly(LocalDate.of(2026, 11, 7));
    }

    @Test
    void returnsZeroFiguresAndNullRates_AC_004_1() {
        var start = Instant.parse("2026-11-06T17:00:00Z");
        var end = Instant.parse("2026-11-07T17:00:00Z");
        when(repository.findFeedback(null, null, start, end))
                .thenReturn(new KnowledgeDashboardResponse.Feedback(0, 0, null, List.of(), List.of()));
        when(repository.findGaps(null, start, end))
                .thenReturn(new KnowledgeDashboardResponse.Gaps(List.of(), 0, List.of()));
        when(repository.findSuggestions(null, null, start, end))
                .thenReturn(new KnowledgeDashboardResponse.Suggestions(0, 0, null));
        var result = service.dashboard(ADMIN, "2026-11-07", "2026-11-07", null, null);
        assertThat(result.outcomes())
                .isEqualTo(new KnowledgeDashboardResponse.Outcomes(
                        0,
                        0,
                        0,
                        null,
                        0,
                        0,
                        List.of(new KnowledgeDashboardResponse.DayOutcome(LocalDate.of(2026, 11, 7), 0, 0, 0)),
                        List.of()));
        assertThat(result.coverage()).isEqualTo(new KnowledgeDashboardResponse.Coverage(0, 0, null, List.of()));
        assertThat(result.feedback())
                .isEqualTo(new KnowledgeDashboardResponse.Feedback(0, 0, null, List.of(), List.of()));
        assertThat(result.gaps()).isEqualTo(new KnowledgeDashboardResponse.Gaps(List.of(), 0, List.of()));
        assertThat(result.suggestions()).isEqualTo(new KnowledgeDashboardResponse.Suggestions(0, 0, null));
    }

    @Test
    void scopesPmToOwnDepartmentAndAcceptsOwnFilter_AC_004_4() {
        when(repository.findUserDepartment(PM.id())).thenReturn(Optional.of(7L));
        when(repository.findDepartment(DEPARTMENT)).thenReturn(Optional.of(new Department(7, DEPARTMENT, "Own")));
        when(repository.findProject(PROJECT)).thenReturn(Optional.of(new Project(PROJECT, 7)));
        assertThat(DashboardScope.resolve(PM, null, null, repository)).isEqualTo(new DashboardScope(Set.of(7L), null));
        assertThat(DashboardScope.resolve(PM, DEPARTMENT.toString(), PROJECT.toString(), repository))
                .isEqualTo(new DashboardScope(Set.of(7L), PROJECT));
        service.dashboard(PM, null, null, DEPARTMENT.toString(), null);
        verify(repository).findOptions(7L);
    }

    @Test
    void rejectsOtherDepartment_AC_004_5() {
        when(repository.findUserDepartment(PM.id())).thenReturn(Optional.of(7L));
        when(repository.findDepartment(DEPARTMENT)).thenReturn(Optional.of(new Department(8, DEPARTMENT, "Other")));
        assertThatThrownBy(() -> service.dashboard(PM, null, null, DEPARTMENT.toString(), null))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void rejectsOtherDepartmentsProject_AC_004_5() {
        when(repository.findUserDepartment(PM.id())).thenReturn(Optional.of(7L));
        when(repository.findProject(PROJECT)).thenReturn(Optional.of(new Project(PROJECT, 8)));
        assertThatThrownBy(() -> service.dashboard(PM, null, null, null, PROJECT.toString()))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void rejectsPmWithoutDepartment_AC_004_5() {
        assertThatThrownBy(() -> service.dashboard(PM, null, null, null, null))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void allowsAdminWorkspaceDepartmentAndProjectScopes_AC_004_6() {
        when(repository.findDepartment(DEPARTMENT)).thenReturn(Optional.of(new Department(7, DEPARTMENT, "Chosen")));
        when(repository.findProject(PROJECT)).thenReturn(Optional.of(new Project(PROJECT, 8)));
        assertThat(DashboardScope.resolve(ADMIN, null, null, repository)).isEqualTo(new DashboardScope(null, null));
        assertThat(DashboardScope.resolve(ADMIN, DEPARTMENT.toString(), null, repository))
                .isEqualTo(new DashboardScope(Set.of(7L), null));
        assertThat(DashboardScope.resolve(ADMIN, null, PROJECT.toString(), repository))
                .isEqualTo(new DashboardScope(null, PROJECT));
        assertThat(DashboardScope.resolve(ADMIN, DEPARTMENT.toString(), PROJECT.toString(), repository))
                .isEqualTo(new DashboardScope(Set.of(7L), PROJECT));
        service.dashboard(ADMIN, null, null, DEPARTMENT.toString(), PROJECT.toString());
        verify(repository).findOptions(null);
    }

    @Test
    void prioritizesAdminRoleOverPmWithoutDepartment_AC_004_6() {
        var both = new TSolveUser(PM.id(), PM.email(), PM.fullName(), Set.of(Role.ADMIN, Role.PROJECT_MANAGER));
        assertThat(DashboardScope.resolve(both, null, null, repository)).isEqualTo(new DashboardScope(null, null));
        verifyNoInteractions(repository);
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "",
                "bad",
                "1-1-1-1-1",
                "00000000000000000000000000000011",
                " 00000000-0000-0000-0000-000000000011",
                "00000000-0000-0000-0000-000000000011 "
            })
    void rejectsMalformedIdsBeforeLookup_AC_004_7(String id) {
        assertThatThrownBy(() -> service.dashboard(ADMIN, null, null, id, null))
                .isInstanceOfSatisfying(InvalidDashboardFilterException.class, e -> assertThat(e.getCode())
                        .isEqualTo("MSG06"));
        assertThatThrownBy(() -> service.dashboard(ADMIN, null, null, DEPARTMENT.toString(), id))
                .isInstanceOfSatisfying(InvalidDashboardFilterException.class, e -> assertThat(e.getCode())
                        .isEqualTo("MSG06"));
        verifyNoInteractions(repository);
    }

    @Test
    void rejectsUnknownIdsBeforeAccess_AC_004_7() {
        assertThatThrownBy(() -> service.dashboard(PM, null, null, DEPARTMENT.toString(), null))
                .isInstanceOfSatisfying(
                        NotFoundException.class, e -> assertThat(e.getCode()).isEqualTo("MSG04"));
        assertThatThrownBy(() -> service.dashboard(PM, null, null, null, PROJECT.toString()))
                .isInstanceOfSatisfying(
                        NotFoundException.class, e -> assertThat(e.getCode()).isEqualTo("MSG04"));
    }

    private static TSolveUser user(Role role) {
        return new TSolveUser(
                UUID.fromString("00000000-0000-0000-0000-000000000001"),
                "user@example.test",
                "Test User",
                Set.of(role));
    }
}
