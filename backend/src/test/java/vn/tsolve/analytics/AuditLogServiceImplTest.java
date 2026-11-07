package vn.tsolve.analytics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import vn.tsolve.analytics.exception.InvalidAuditFilterException;
import vn.tsolve.analytics.mapper.AuditEntryMapper;
import vn.tsolve.analytics.repository.AuditLogEntryRepository;
import vn.tsolve.analytics.service.impl.AuditLogServiceImpl;

class AuditLogServiceImplTest {
    private final AuditLogEntryRepository repository = mock(AuditLogEntryRepository.class);
    private AuditLogServiceImpl service;

    @BeforeEach
    void setUp() {
        var beans = new StaticListableBeanFactory();
        beans.addBean("clock", Clock.fixed(Instant.parse("2026-11-06T18:00:00Z"), ZoneOffset.UTC));
        service = new AuditLogServiceImpl(repository, mock(AuditEntryMapper.class), beans.getBeanProvider(Clock.class));
    }

    @Test
    void usesDefaultSortAndStableTieBreaker_AC_003_2() {
        when(repository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(Page.empty());
        service.findEntries(null, null, null, null, 0, 20, List.of());
        var paging = ArgumentCaptor.forClass(Pageable.class);
        verify(repository).findAll(any(Specification.class), paging.capture());
        assertThat(paging.getValue().getPageSize()).isEqualTo(20);
        assertThat(paging.getValue().getSort()).containsExactly(Sort.Order.desc("createdAt"), Sort.Order.asc("id"));
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
                "2026-11-07T00:00:00Z,2026-11-07"
            },
            nullValues = "NULL")
    void rejectsInvalidDates_AC_003_6(String from, String to) {
        assertThatThrownBy(() -> service.findEntries(null, null, from, to, 0, 20, List.of()))
                .isInstanceOfSatisfying(InvalidAuditFilterException.class, e -> assertThat(e.getCode())
                        .isEqualTo("MSG39"));
        verifyNoInteractions(repository);
    }

    @Test
    void acceptsTodayInVietnamWhileUtcIsYesterday_AC_003_6() {
        when(repository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(Page.empty());
        assertThat(service.findEntries(null, null, "2026-11-07", "2026-11-07", 0, 20, List.of()))
                .isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"approve", "", "UNKNOWN"})
    void rejectsInvalidAction_AC_003_7(String action) {
        invalid(action, 0, 20, List.of());
    }

    @ParameterizedTest
    @ValueSource(strings = {"id", "createdAt,ASC", "createdAt,desc,extra", "", "actor,asc"})
    void rejectsInvalidSort_AC_003_7(String sort) {
        invalid(null, 0, 20, List.of(sort));
    }

    @Test
    void rejectsInvalidPagingAndMultipleSorts_AC_003_7() {
        invalid(null, -1, 20, List.of());
        invalid(null, 0, 0, List.of());
        invalid(null, 0, 101, List.of());
        invalid(null, 0, 20, List.of("createdAt", "createdAt,desc"));
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"createdAt", "createdAt,asc", "createdAt,desc"})
    void acceptsSupportedSorts_AC_003_2(String sort) {
        when(repository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(Page.empty());
        service.findEntries(null, null, null, null, 0, 20, sort == null ? List.of() : List.of(sort));
        var paging = ArgumentCaptor.forClass(Pageable.class);
        verify(repository).findAll(any(Specification.class), paging.capture());
        assertThat(paging.getValue().getSort())
                .containsExactly(
                        sort == null || sort.endsWith("desc")
                                ? Sort.Order.desc("createdAt")
                                : Sort.Order.asc("createdAt"),
                        Sort.Order.asc("id"));
    }

    private void invalid(String action, int page, int size, List<String> sort) {
        assertThatThrownBy(() -> service.findEntries(null, action, null, null, page, size, sort))
                .isInstanceOfSatisfying(InvalidAuditFilterException.class, e -> assertThat(e.getCode())
                        .isEqualTo("MSG06"));
        verifyNoInteractions(repository);
    }
}
