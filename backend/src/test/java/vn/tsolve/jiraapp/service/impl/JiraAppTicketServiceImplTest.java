package vn.tsolve.jiraapp.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.data.domain.PageImpl;
import vn.tsolve.common.security.JiraAppPrincipal;
import vn.tsolve.common.security.TSolveUser;
import vn.tsolve.identity.service.ScopeAccessService;
import vn.tsolve.jiraapp.dto.JiraAppTicketPreview;
import vn.tsolve.jiraapp.dto.JiraAppTicketResult;
import vn.tsolve.jiraapp.exception.JiraIssueAccessDeniedException;
import vn.tsolve.jiraapp.service.JiraIssueAccess;
import vn.tsolve.search.query.dto.TicketSearchResultResponse;
import vn.tsolve.search.query.service.TicketSearchService;
import vn.tsolve.search.scope.dto.TicketDepartmentRef;
import vn.tsolve.ticket.dto.PublishedTicketView;
import vn.tsolve.ticket.dto.SolutionSections;
import vn.tsolve.ticket.service.PublishedTicketService;

class JiraAppTicketServiceImplTest {

    private static final TSolveUser USER =
            new TSolveUser(UUID.fromString("00000000-0000-0000-0000-000000000001"), "b@x.test", "Ngo Bao", Set.of());
    private static final JiraAppPrincipal CALLER = new JiraAppPrincipal("cloud-1", "jira-1", null);
    private static final UUID IT = UUID.fromString("00000000-0000-0000-0000-0000000000d1");
    private static final UUID HR = UUID.fromString("00000000-0000-0000-0000-0000000000d2");
    private static final UUID FIRST = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
    private static final UUID SECOND = UUID.fromString("00000000-0000-0000-0000-0000000000a2");
    private static final UUID HIDDEN = UUID.fromString("00000000-0000-0000-0000-0000000000a3");
    private static final SolutionSections SOLUTION = new SolutionSections("Drops", "Old", "Update", "", "Laptops");

    private final JiraIssueAccess access = mock(JiraIssueAccess.class);
    private final TicketSearchService search = mock(TicketSearchService.class);
    private final PublishedTicketService published = mock(PublishedTicketService.class);
    private final ScopeAccessService scopes = mock(ScopeAccessService.class);
    private final JiraAppTicketServiceImpl service =
            new JiraAppTicketServiceImpl(access, search, published, scopes, "https://acme.tsolve.io.vn/");

    @BeforeEach
    void linkedUser() {
        given(access.asUser(eq(CALLER), any())).willAnswer(call -> {
            Function<TSolveUser, Object> work = call.getArgument(1);
            return work.apply(USER);
        });
        given(scopes.canAccessDepartment(USER.id(), IT)).willReturn(true);
        given(scopes.canAccessDepartment(USER.id(), HR)).willReturn(false);
    }

    @Test
    void listsPublishedTicketsOfTheUsersDepartmentsInRankOrder_AC_008_4() {
        given(search.search(eq(USER), any()))
                .willReturn(new PageImpl<>(List.of(result(SECOND, IT), result(HIDDEN, HR), result(FIRST, IT))));
        given(published.findPublished(List.of(SECOND, FIRST)))
                .willReturn(List.of(view(FIRST, "ITSUP-1", IT), view(SECOND, "ITSUP-2", IT)));

        List<JiraAppTicketResult> results = service.search(CALLER, "vpn");

        assertThat(results)
                .containsExactly(
                        new JiraAppTicketResult(SECOND, "ITSUP-2", "Title ITSUP-2", "IT"),
                        new JiraAppTicketResult(FIRST, "ITSUP-1", "Title ITSUP-1", "IT"));
    }

    @Test
    void findsNothingWhenNoResultIsVisible_AC_008_5() {
        given(search.search(eq(USER), any())).willReturn(new PageImpl<>(List.of(result(HIDDEN, HR))));

        assertThat(service.search(CALLER, "vpn")).isEmpty();
    }

    @Test
    void previewsAPublishedTicketOfTheUsersDepartment_AC_009_1() {
        given(published.findPublished(List.of(FIRST))).willReturn(List.of(view(FIRST, "ITSUP-1", IT)));

        JiraAppTicketPreview preview = service.preview(CALLER, FIRST);

        assertThat(preview)
                .isEqualTo(new JiraAppTicketPreview(
                        FIRST,
                        "ITSUP-1",
                        "Title ITSUP-1",
                        "IT",
                        SOLUTION,
                        "https://acme.tsolve.io.vn/tickets/" + FIRST));
    }

    @Test
    void refusesATicketOfAnotherDepartmentOrNotPublished_AC_009_2() {
        given(published.findPublished(List.of(HIDDEN))).willReturn(List.of(view(HIDDEN, "HR-1", HR)));
        given(published.findPublished(List.of(SECOND))).willReturn(List.of());

        assertThatThrownBy(() -> service.preview(CALLER, HIDDEN)).isInstanceOf(JiraIssueAccessDeniedException.class);
        assertThatThrownBy(() -> service.preview(CALLER, SECOND)).isInstanceOf(JiraIssueAccessDeniedException.class);
    }

    private static TicketSearchResultResponse result(UUID id, UUID department) {
        return new TicketSearchResultResponse(
                id,
                "t",
                null,
                new TicketDepartmentRef(department, department.equals(IT) ? "IT" : "HR"),
                "",
                null,
                null);
    }

    private static PublishedTicketView view(UUID id, String ticketId, UUID department) {
        return new PublishedTicketView(
                id, ticketId, "Title " + ticketId, department, department.equals(IT) ? "IT" : "HR", SOLUTION);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"  ", "/tickets", "acme.tsolve.io.vn", "ftp://acme.tsolve.io.vn", "https://"})
    void refusesToStartWithoutAnAbsoluteWebOrigin_BUG_205(String webOrigin) {
        assertThatThrownBy(() -> new JiraAppTicketServiceImpl(access, search, published, scopes, webOrigin))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("TSOLVE_WEB_ORIGIN");
    }
}
