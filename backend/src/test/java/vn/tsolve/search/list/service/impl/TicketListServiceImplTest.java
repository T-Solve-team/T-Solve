package vn.tsolve.search.list.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import vn.tsolve.common.exception.CodedException;
import vn.tsolve.common.security.TSolveUser;
import vn.tsolve.search.list.dto.TicketListQuery;
import vn.tsolve.search.list.mapper.TicketSummaryMapper;
import vn.tsolve.search.list.repository.TicketListRepository;
import vn.tsolve.search.scope.entity.SearchTicket;
import vn.tsolve.search.scope.service.VisibleScope;

class TicketListServiceImplTest {
    private final TicketListRepository repository = mock(TicketListRepository.class);
    private final VisibleScope visibleScope = mock(VisibleScope.class);
    private final TicketSummaryMapper mapper = mock(TicketSummaryMapper.class);
    private final TSolveUser user = new TSolveUser(UUID.randomUUID(), "resolver@example.test", "Resolver", Set.of());
    private final TicketListServiceImpl service = new TicketListServiceImpl(repository, visibleScope, mapper);

    @BeforeEach
    void scope() {
        when(visibleScope.of(user)).thenReturn(new VisibleScope.Scope(Set.of(1L), Set.of(7L), Optional.of("writer")));
    }

    @Test
    void returnsEmptyPageForEmptyScope_AC_006_2() {
        when(visibleScope.of(user)).thenReturn(VisibleScope.Scope.empty());
        var page = service.list(user, query(null, null, null, null, 2, 10, null));
        assertThat(page.getNumber()).isEqualTo(2);
        assertThat(page.getSize()).isEqualTo(10);
        assertThat(page.getContent()).isEmpty();
        verifyNoInteractions(repository, mapper);
    }

    @Test
    void usesDefaultPageAndStableUpdatedSort_AC_006_3() {
        when(repository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<SearchTicket>(List.of()));
        service.list(user, query(null, null, null, null, null, null, null));
        ArgumentCaptor<Pageable> paging = ArgumentCaptor.forClass(Pageable.class);
        verify(repository).findAll(any(Specification.class), paging.capture());
        assertThat(paging.getValue().getPageNumber()).isZero();
        assertThat(paging.getValue().getPageSize()).isEqualTo(20);
        assertThat(paging.getValue().getSort()).isEqualTo(Sort.by(Sort.Order.desc("updatedAt"), Sort.Order.desc("id")));
    }

    @Test
    void outsideDepartmentNarrowsEvenManagedProjects_AC_006_4() {
        UUID department = UUID.randomUUID();
        when(repository.findDepartmentId(department)).thenReturn(Optional.of(99L));
        var page = service.list(user, query(null, department.toString(), null, null, null, null, null));
        assertThat(page.getContent()).isEmpty();
        verify(repository).findDepartmentId(department);
        org.mockito.Mockito.verifyNoMoreInteractions(repository);
        verifyNoInteractions(mapper);
    }

    @Test
    void acceptsReviewDueDateWithIdTieBreakOnly_AC_006_7() {
        when(repository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<SearchTicket>(List.of()));
        service.list(user, query(null, null, null, null, null, null, "reviewDueDate,asc"));
        ArgumentCaptor<Pageable> paging = ArgumentCaptor.forClass(Pageable.class);
        verify(repository).findAll(any(Specification.class), paging.capture());
        assertThat(paging.getValue().getSort()).isEqualTo(Sort.by(Sort.Order.desc("id")));
    }

    @ParameterizedTest
    @MethodSource("invalidQueries")
    void rejectsInvalidFiltersBeforeScopeOrDatabase_AC_006_9(TicketListQuery query) {
        assertThatThrownBy(() -> service.list(user, query))
                .isInstanceOf(CodedException.class)
                .satisfies(
                        error -> assertThat(((CodedException) error).getCode()).isEqualTo("MSG06"));
        verifyNoInteractions(visibleScope, repository, mapper);
    }

    @Test
    void reportsKeywordLengthOnTheField_AC_006_9() {
        assertThatThrownBy(() -> service.list(user, query("x".repeat(101), null, null, null, null, null, null)))
                .isInstanceOf(CodedException.class)
                .satisfies(error -> {
                    CodedException coded = (CodedException) error;
                    assertThat(coded.getCode()).isEqualTo("MSG02");
                    assertThat(coded.getField()).isEqualTo("keyword");
                    assertThat(coded.getParams()).containsEntry("max_length", 100);
                });
        verifyNoInteractions(visibleScope, repository, mapper);
    }

    @Test
    void returnsEmptyPastJpaOffsetLimitWithCorrectTotal_AC_006_3() {
        when(repository.findAll(any(Specification.class), any(Pageable.class)))
                .thenThrow(new IllegalArgumentException("Offset exceeds JPA limit"));
        when(repository.count(any(Specification.class))).thenReturn(3L);
        var page = service.list(user, query(null, null, null, null, Integer.MAX_VALUE, 100, null));
        assertThat(page.getContent()).isEmpty();
        assertThat(page.getTotalElements()).isEqualTo(3);
        assertThat(page.getNumber()).isEqualTo(Integer.MAX_VALUE);
        verify(repository).count(any(Specification.class));
    }

    private static Stream<TicketListQuery> invalidQueries() {
        return Stream.of(
                query(null, "bad", null, null, null, null, null),
                query(null, "1-1-1-1-1", null, null, null, null, null),
                query(null, null, "OTHER", null, null, null, null),
                query(null, null, null, "DEFERRED", null, null, null),
                query(null, null, null, null, -1, null, null),
                query(null, null, null, null, null, 0, null),
                query(null, null, null, null, null, 101, null),
                query(null, null, null, null, null, null, "writer,asc"),
                query(null, null, null, null, null, null, "updatedAt,sideways"),
                query(null, null, null, null, null, null, "title,asc,desc"));
    }

    private static TicketListQuery query(
            String keyword, String department, String source, String status, Integer page, Integer size, String sort) {
        return new TicketListQuery(keyword, department, source, status, page, size, sort);
    }
}
