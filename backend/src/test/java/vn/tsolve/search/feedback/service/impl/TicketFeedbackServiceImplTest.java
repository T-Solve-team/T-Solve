package vn.tsolve.search.feedback.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import vn.tsolve.common.exception.NotFoundException;
import vn.tsolve.common.security.Role;
import vn.tsolve.common.security.TSolveUser;
import vn.tsolve.search.feedback.dto.TicketFeedbackRequest;
import vn.tsolve.search.feedback.repository.FeedbackTicketRepository;
import vn.tsolve.search.feedback.repository.TicketFeedbackRepository;
import vn.tsolve.search.scope.entity.SearchTicket;
import vn.tsolve.search.scope.service.VisibleScope;

class TicketFeedbackServiceImplTest {
    private final FeedbackTicketRepository tickets = mock(FeedbackTicketRepository.class);
    private final TicketFeedbackRepository feedback = mock(TicketFeedbackRepository.class);
    private final VisibleScope visibleScope = mock(VisibleScope.class);
    private final TicketFeedbackServiceImpl service = new TicketFeedbackServiceImpl(tickets, feedback, visibleScope);
    private final TSolveUser user =
            new TSolveUser(UUID.randomUUID(), "resolver@example.test", "Resolver", Set.of(Role.TICKET_RESOLVER));
    private final UUID publicId = UUID.randomUUID();

    @ParameterizedTest
    @MethodSource("validFeedback")
    void storesRatingTrimmedCommentAndWebChannel_AC_008_1(boolean isHelpful, String comment, String storedComment) {
        SearchTicket ticket = mock(SearchTicket.class);
        when(visibleScope.of(user)).thenReturn(new VisibleScope.Scope(Set.of(1L), Set.of(), Optional.empty()));
        when(tickets.findByPublicId(publicId)).thenReturn(Optional.of(ticket));
        when(ticket.getStatus()).thenReturn("PUBLISHED");
        when(ticket.getDepartmentStatus()).thenReturn("ACTIVE");
        when(ticket.getDepartmentId()).thenReturn(1L);
        when(ticket.getId()).thenReturn(42L);

        service.give(user, publicId, new TicketFeedbackRequest(isHelpful, comment));

        verify(feedback).upsert(42L, user.id(), isHelpful ? "HELPFUL" : "NOT_HELPFUL", storedComment, "WEB");
    }

    static Stream<Arguments> validFeedback() {
        return Stream.of(
                Arguments.of(true, "  Useful solution \n", "Useful solution"),
                Arguments.of(false, "  Needs detail  ", "Needs detail"),
                Arguments.of(true, null, null),
                Arguments.of(false, "", null),
                Arguments.of(true, " \t\r\n ", null),
                Arguments.of(true, "x".repeat(500), "x".repeat(500)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"empty", "missing", "unpublished", "deleted", "outside", "managedOnly", "writerOnly"})
    void answers404ForTicketOutsideScope_AC_008_3(String scenario) {
        VisibleScope.Scope scope =
                switch (scenario) {
                    case "empty" -> VisibleScope.Scope.empty();
                    case "managedOnly" -> new VisibleScope.Scope(Set.of(), Set.of(7L), Optional.empty());
                    case "writerOnly" -> new VisibleScope.Scope(Set.of(), Set.of(), Optional.of("resolver"));
                    default -> new VisibleScope.Scope(Set.of(1L), Set.of(7L), Optional.of("resolver"));
                };
        when(visibleScope.of(user)).thenReturn(scope);
        if (!"empty".equals(scenario)) {
            if ("missing".equals(scenario)) {
                when(tickets.findByPublicId(publicId)).thenReturn(Optional.empty());
            } else {
                SearchTicket ticket = mock(SearchTicket.class);
                when(tickets.findByPublicId(publicId)).thenReturn(Optional.of(ticket));
                when(ticket.getStatus()).thenReturn("unpublished".equals(scenario) ? "UNPUBLISHED" : "PUBLISHED");
                when(ticket.getDepartmentStatus()).thenReturn("deleted".equals(scenario) ? "DELETED" : "ACTIVE");
                when(ticket.getDepartmentId()).thenReturn("outside".equals(scenario) ? 2L : 1L);
            }
        }

        assertThatThrownBy(() -> service.give(user, publicId, new TicketFeedbackRequest(true, "comment")))
                .isInstanceOfSatisfying(NotFoundException.class, error -> assertThat(error.getCode())
                        .isEqualTo("MSG04"));
        verifyNoInteractions(feedback);
        if ("empty".equals(scenario)) verifyNoInteractions(tickets);
    }
}
