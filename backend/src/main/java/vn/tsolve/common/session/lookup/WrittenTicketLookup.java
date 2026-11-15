package vn.tsolve.common.session.lookup;

import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import vn.tsolve.common.session.dto.MySolution;
import vn.tsolve.common.session.dto.MySolutionFilter;

/**
 * Implemented by ticket-repository. Returns, in the current tenant (from {@code TenantContext}), the Tickets whose
 * Solution the Account wrote, matching the filter, newest {@code updatedAt} first; never null. Drafts, a deleted
 * Department and the history rules are the implementer's; it maps its own {@code TicketStatus} and
 * {@code ReviewDecision} to the ones in {@code common.session.dto}. Until a bean exists, callers answer an empty page.
 */
public interface WrittenTicketLookup {
    Page<MySolution> findSolutions(UUID accountId, MySolutionFilter filter, Pageable page);
}
