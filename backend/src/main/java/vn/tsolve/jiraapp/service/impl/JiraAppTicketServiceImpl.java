package vn.tsolve.jiraapp.service.impl;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import vn.tsolve.common.security.JiraAppPrincipal;
import vn.tsolve.identity.service.ScopeAccessService;
import vn.tsolve.jiraapp.dto.JiraAppTicketPreview;
import vn.tsolve.jiraapp.dto.JiraAppTicketResult;
import vn.tsolve.jiraapp.exception.JiraIssueAccessDeniedException;
import vn.tsolve.jiraapp.service.JiraAppTicketService;
import vn.tsolve.jiraapp.service.JiraIssueAccess;
import vn.tsolve.search.query.dto.TicketSearchQuery;
import vn.tsolve.search.query.dto.TicketSearchResultResponse;
import vn.tsolve.search.query.service.TicketSearchService;
import vn.tsolve.ticket.dto.PublishedTicketView;
import vn.tsolve.ticket.service.PublishedTicketService;

@Service
public class JiraAppTicketServiceImpl implements JiraAppTicketService {

    /** The panel shows one short list (contract: at most 20). */
    static final int MAX_RESULTS = 20;

    private final JiraIssueAccess access;
    private final TicketSearchService search;
    private final PublishedTicketService published;
    private final ScopeAccessService scopes;
    private final String webOrigin;

    public JiraAppTicketServiceImpl(
            JiraIssueAccess access,
            TicketSearchService search,
            PublishedTicketService published,
            ScopeAccessService scopes,
            @Value("${TSOLVE_WEB_ORIGIN:}") String webOrigin) {
        this.access = access;
        this.search = search;
        this.published = published;
        this.scopes = scopes;
        this.webOrigin = webOrigin == null ? "" : webOrigin.strip().replaceAll("/+$", "");
    }

    @Override
    public List<JiraAppTicketResult> search(JiraAppPrincipal caller, String q) {
        TicketSearchQuery query = TicketSearchQuery.parse(q, null, null, null, null, "0", String.valueOf(MAX_RESULTS));
        return access.asUser(caller, user -> {
            // search ranks within its own visible scope; the Department rule is checked again here (GBR-02).
            List<TicketSearchResultResponse> ranked = search.search(user, query).getContent().stream()
                    .filter(result -> result.department() != null
                            && scopes.canAccessDepartment(
                                    user.id(), result.department().departmentId()))
                    .toList();
            Map<UUID, PublishedTicketView> tickets =
                    published
                            .findPublished(ranked.stream()
                                    .map(TicketSearchResultResponse::ticketId)
                                    .toList())
                            .stream()
                            .collect(Collectors.toMap(PublishedTicketView::id, Function.identity()));
            return ranked.stream()
                    .filter(result -> tickets.containsKey(result.ticketId()))
                    .map(result -> {
                        PublishedTicketView ticket = tickets.get(result.ticketId());
                        return new JiraAppTicketResult(
                                ticket.id(), ticket.ticketId(), ticket.title(), ticket.departmentName());
                    })
                    .toList();
        });
    }

    @Override
    public JiraAppTicketPreview preview(JiraAppPrincipal caller, UUID ticketId) {
        return access.asUser(caller, user -> published.findPublished(List.of(ticketId)).stream()
                .filter(ticket -> scopes.canAccessDepartment(user.id(), ticket.departmentId()))
                .findFirst()
                .map(ticket -> new JiraAppTicketPreview(
                        ticket.id(),
                        ticket.ticketId(),
                        ticket.title(),
                        ticket.departmentName(),
                        ticket.solution(),
                        webOrigin + "/tickets/" + ticket.id()))
                .orElseThrow(JiraIssueAccessDeniedException::new));
    }
}
