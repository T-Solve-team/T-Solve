package vn.tsolve.search.suggestion.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import vn.tsolve.common.security.Role;
import vn.tsolve.common.security.TSolveUser;
import vn.tsolve.search.query.dto.RankFilter;
import vn.tsolve.search.query.dto.RankedPage;
import vn.tsolve.search.query.dto.TicketSearchResultResponse;
import vn.tsolve.search.query.repository.SearchLogRepository;
import vn.tsolve.search.query.service.TicketRankingService;
import vn.tsolve.search.scope.service.VisibleScope;
import vn.tsolve.search.suggestion.dto.TicketSuggestionRequest;

class TicketSuggestionServiceImplTest {
    private final VisibleScope visibleScope = mock(VisibleScope.class);
    private final TicketRankingService ranking = mock(TicketRankingService.class);
    private final SearchLogRepository logs = mock(SearchLogRepository.class);
    private final TicketSuggestionServiceImpl service = new TicketSuggestionServiceImpl(visibleScope, ranking, logs);
    private final TSolveUser user =
            new TSolveUser(UUID.randomUUID(), "writer@example.test", "Writer", Set.of(Role.TICKET_RESOLVER));
    private final VisibleScope.Scope scope = new VisibleScope.Scope(Set.of(7L), Set.of(9L), Optional.of("Writer"));
    private final PageRequest pageRequest = PageRequest.of(0, 5);
    private final RankFilter noFilter = new RankFilter(null, null, null, null);

    @BeforeEach
    void setUp() {
        when(visibleScope.of(user)).thenReturn(scope);
    }

    @Test
    void ranksSummaryAndDescriptionTopFive_AC_009_1() {
        List<TicketSearchResultResponse> items = List.of(item("First"), item("Second"));
        Page<TicketSearchResultResponse> page = new PageImpl<>(items, pageRequest, 12);
        when(ranking.rank(any(), any(), any(), any(), any())).thenReturn(new RankedPage(page, false));

        assertThat(service.suggest(user, new TicketSuggestionRequest("  Printer jams  ", "  Paper stuck\n ")))
                .containsExactlyElementsOf(items);
        verify(ranking).rank("Printer jams\n\nPaper stuck", "Printer jams", noFilter, scope, pageRequest);
        for (String description : new String[] {null, "", "   "}) {
            assertThat(service.suggest(user, new TicketSuggestionRequest("  Printer jams  ", description)))
                    .containsExactlyElementsOf(items);
        }
        verify(ranking, times(3)).rank("Printer jams", "Printer jams", noFilter, scope, pageRequest);
        verify(visibleScope, times(4)).of(user);
        verifyNoMoreInteractions(ranking, visibleScope);
    }

    @Test
    void fallsBackToSummaryKeyword_AC_009_2() {
        List<TicketSearchResultResponse> items = List.of(item("Keyword result"));
        when(ranking.rank(any(), any(), any(), any(), any()))
                .thenReturn(new RankedPage(new PageImpl<>(items, pageRequest, 20), true));

        assertThat(service.suggest(user, new TicketSuggestionRequest("  Printer jams  ", "  Paper stuck  ")))
                .containsExactlyElementsOf(items);
        verify(ranking).rank("Printer jams\n\nPaper stuck", "Printer jams", noFilter, scope, pageRequest);
        verify(logs).log(user.id(), null, "Printer jams", "ISSUE_PANEL", 1, true);
    }

    @Test
    void logsIssuePanelRowWithReturnedCount_AC_009_3() {
        List<TicketSearchResultResponse> items = List.of(item("First"), item("Second"), item("Third"));
        when(ranking.rank(any(), any(), any(), any(), any()))
                .thenReturn(new RankedPage(new PageImpl<>(items, pageRequest, 43), false))
                .thenReturn(new RankedPage(Page.empty(pageRequest), true));

        assertThat(service.suggest(user, new TicketSuggestionRequest("  Printer jams  ", null)))
                .containsExactlyElementsOf(items);
        verify(logs).log(user.id(), null, "Printer jams", "ISSUE_PANEL", 3, false);
        assertThat(service.suggest(user, new TicketSuggestionRequest("  Nothing  ", "")))
                .isEmpty();
        verify(logs).log(user.id(), null, "Nothing", "ISSUE_PANEL", 0, true);
        verifyNoMoreInteractions(logs);
    }

    @Test
    void returnsSuggestionsWhenSearchLogFails_AC_009_3() {
        String summary = "private-summary-8483";
        String description = "private-description-8483";
        List<TicketSearchResultResponse> items = List.of(item("private-ticket-8483"));
        when(ranking.rank(any(), any(), any(), any(), any())).thenReturn(new RankedPage(new PageImpl<>(items), false));
        doThrow(new RuntimeException(summary + description + items.getFirst().title()))
                .when(logs)
                .log(user.id(), null, summary, "ISSUE_PANEL", 1, false);
        Logger logger = (Logger) LoggerFactory.getLogger(TicketSuggestionServiceImpl.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            assertThat(service.suggest(user, new TicketSuggestionRequest(summary, description)))
                    .containsExactlyElementsOf(items);
            verify(logs).log(user.id(), null, summary, "ISSUE_PANEL", 1, false);
            assertThat(appender.list).singleElement().satisfies(event -> {
                assertThat(event.getLevel()).isEqualTo(Level.WARN);
                assertThat(event.getFormattedMessage())
                        .contains("RuntimeException")
                        .doesNotContain(summary, description, items.getFirst().title());
                assertThat(event.getThrowableProxy()).isNull();
            });
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }

    private TicketSearchResultResponse item(String title) {
        return new TicketSearchResultResponse(UUID.randomUUID(), title, null, null, "excerpt", null, null);
    }
}
