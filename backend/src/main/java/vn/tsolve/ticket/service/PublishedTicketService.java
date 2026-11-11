package vn.tsolve.ticket.service;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import vn.tsolve.ticket.dto.PublishedTicketView;

/** Published tickets read by public id, for the Jira App's search results and preview (jira-app STORY-008, 009). */
public interface PublishedTicketService {

    /** The PUBLISHED tickets of ACTIVE Departments among {@code ids}; any other id is left out. */
    List<PublishedTicketView> findPublished(Collection<UUID> ids);
}
