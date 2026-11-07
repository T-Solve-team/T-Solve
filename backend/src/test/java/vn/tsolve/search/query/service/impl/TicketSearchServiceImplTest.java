package vn.tsolve.search.query.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import vn.tsolve.common.security.Role;
import vn.tsolve.common.security.TSolveUser;
import vn.tsolve.search.query.dto.RankedPage;
import vn.tsolve.search.query.dto.TicketSearchQuery;
import vn.tsolve.search.query.dto.TicketSearchResultResponse;
import vn.tsolve.search.query.repository.SearchLogRepository;
import vn.tsolve.search.query.service.TicketRankingService;
import vn.tsolve.search.scope.service.VisibleScope;

class TicketSearchServiceImplTest {
    private final VisibleScope visibleScope = mock(VisibleScope.class);
    private final TicketRankingService ranking = mock(TicketRankingService.class);
    private final SearchLogRepository logs = mock(SearchLogRepository.class);
    private final TicketSearchServiceImpl service = new TicketSearchServiceImpl(visibleScope, ranking, logs);
    private final TSolveUser user =
            new TSolveUser(UUID.randomUUID(), "writer@example.test", "Writer", Set.of(Role.TICKET_RESOLVER));
    private final VisibleScope.Scope scope = new VisibleScope.Scope(Set.of(7L), Set.of(), Optional.empty());

    @Test
    void returnsResultsWhenSearchLogFailsWithoutLoggingQuery_BUG_143() {
        String privateQuery = "private-query-8483";
        Page<TicketSearchResultResponse> page = new PageImpl<>(
                List.of(new TicketSearchResultResponse(UUID.randomUUID(), "VPN", null, null, "excerpt", null, null)));
        when(visibleScope.of(user)).thenReturn(scope);
        when(ranking.rank(any(), any(), any(), any(), any())).thenReturn(new RankedPage(page, false));
        doThrow(new DataAccessResourceFailureException(privateQuery))
                .when(logs)
                .log(user.id(), null, privateQuery, "WEB", 1, false);
        Logger logger = (Logger) LoggerFactory.getLogger(TicketSearchServiceImpl.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            assertThat(service.search(user, TicketSearchQuery.parse(privateQuery, null, null, null, null, null, null)))
                    .isSameAs(page);
            verify(logs).log(user.id(), null, privateQuery, "WEB", 1, false);
            assertThat(appender.list).singleElement().satisfies(event -> {
                assertThat(event.getLevel()).isEqualTo(Level.WARN);
                assertThat(event.getFormattedMessage())
                        .contains("DataAccessResourceFailureException")
                        .doesNotContain(privateQuery);
                assertThat(event.getThrowableProxy()).isNull();
            });
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }

    @Test
    void logsPageZeroOnlyWithFallbackFlag_AC_007_6() {
        UUID department = UUID.randomUUID();
        when(visibleScope.of(user)).thenReturn(scope);
        when(ranking.rank(eq("VPN"), eq("VPN"), any(), eq(scope), any())).thenAnswer(call -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive())
                    .isFalse();
            PageRequest page = call.getArgument(4);
            TicketSearchResultResponse row =
                    new TicketSearchResultResponse(UUID.randomUUID(), "VPN", null, null, "excerpt", null, null);
            return new RankedPage(new PageImpl<>(List.of(row), page, 43), true);
        });
        var first = service.search(
                user, TicketSearchQuery.parse("  VPN  ", department.toString(), "CSV", null, null, null, null));
        assertThat(first.getTotalElements()).isEqualTo(43);
        service.search(user, TicketSearchQuery.parse("VPN", department.toString(), "CSV", null, null, "1", "20"));
        verify(logs).log(user.id(), department.toString(), "VPN", "WEB", 43, true);

        when(ranking.rank(any(), any(), any(), any(), any()))
                .thenReturn(new RankedPage(Page.empty(PageRequest.of(0, 20)), false));
        service.search(user, TicketSearchQuery.parse("nothing", null, null, null, null, null, null));
        verify(logs).log(user.id(), null, "nothing", "WEB", 0, false);
        verifyNoMoreInteractions(logs);
        verify(visibleScope, times(3)).of(user);
    }

    @Test
    void neverLogsQueryText_AC_007_9() {
        String privateQuery = "private-query-8483";
        when(visibleScope.of(user)).thenReturn(scope);
        when(ranking.rank(any(), any(), any(), any(), any()))
                .thenReturn(new RankedPage(Page.empty(PageRequest.of(0, 20)), true));
        Logger root = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        root.addAppender(appender);
        try {
            service.search(user, TicketSearchQuery.parse(privateQuery, null, null, null, null, null, null));
            assertThat(appender.list)
                    .noneMatch(event -> event.getFormattedMessage().contains(privateQuery));
            verify(ranking).rank(eq(privateQuery), eq(privateQuery), any(), eq(scope), any());
            verify(logs).log(user.id(), null, privateQuery, "WEB", 0, true);
        } finally {
            root.detachAppender(appender);
            appender.stop();
        }
    }
}
