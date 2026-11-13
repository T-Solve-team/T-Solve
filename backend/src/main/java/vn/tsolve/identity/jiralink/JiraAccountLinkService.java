package vn.tsolve.identity.jiralink;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import vn.tsolve.common.exception.MessageCodeException;

/** Records the link of a tracker account to an Account (AC-001.71, AC-001.38). */
@Service
public class JiraAccountLinkService {

    private static final Logger log = LoggerFactory.getLogger(JiraAccountLinkService.class);
    private static final String MSG06 = "MSG06";

    private final JiraAccountLinkRepository links;

    public JiraAccountLinkService(JiraAccountLinkRepository links) {
        this.links = links;
    }

    /**
     * Links the tracker account to the Account, replacing an earlier link of the same tracker account.
     *
     * @throws LinkNotRecordedException 500 MSG06 when the database refuses the write; no SQL text leaves this method
     */
    public void link(String trackerAccountId, UUID accountId, Instant linkedAt) {
        try {
            links.upsert(new JiraAccountLink(trackerAccountId, accountId, linkedAt));
        } catch (DataAccessException e) {
            // class name only: the message carries SQL
            log.error("Recording a Jira account link failed ({})", e.getClass().getName());
            throw new LinkNotRecordedException();
        }
    }

    /** The link could not be recorded: 500 MSG06. */
    public static class LinkNotRecordedException extends MessageCodeException {
        LinkNotRecordedException() {
            super(HttpStatus.INTERNAL_SERVER_ERROR, MSG06, Map.of());
        }
    }
}
