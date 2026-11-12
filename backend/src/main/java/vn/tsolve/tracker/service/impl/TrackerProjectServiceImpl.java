package vn.tsolve.tracker.service.impl;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import vn.tsolve.tracker.client.TicketTrackerClient;
import vn.tsolve.tracker.dto.JiraAccount;
import vn.tsolve.tracker.dto.TrackerProjectLookup;
import vn.tsolve.tracker.dto.TrackerProjectLookup.Outcome;
import vn.tsolve.tracker.dto.TrackerStatus;
import vn.tsolve.tracker.exception.JiraRejectedException;
import vn.tsolve.tracker.exception.JiraUnavailableException;
import vn.tsolve.tracker.service.JiraServiceAccount;
import vn.tsolve.tracker.service.TrackerProjectService;

@Service
public class TrackerProjectServiceImpl implements TrackerProjectService {

    private static final Logger log = LoggerFactory.getLogger(TrackerProjectServiceImpl.class);

    private final JiraServiceAccount serviceAccount;
    private final TicketTrackerClient jira;

    public TrackerProjectServiceImpl(JiraServiceAccount serviceAccount, TicketTrackerClient jira) {
        this.serviceAccount = serviceAccount;
        this.jira = jira;
    }

    @Override
    public TrackerProjectLookup findProject(String projectKey) {
        try {
            Optional<JiraAccount> account = serviceAccount.current();
            if (account.isEmpty()) {
                log.warn("Jira project lookup {}: {} (no service account)", projectKey, Outcome.DENIED);
                return TrackerProjectLookup.failed(Outcome.DENIED);
            }
            Optional<String> canonicalKey = jira.findProject(account.get(), projectKey);
            if (canonicalKey.isEmpty()) {
                log.warn("Jira project lookup {}: {} (Jira answered 404)", projectKey, Outcome.NOT_FOUND);
                return TrackerProjectLookup.failed(Outcome.NOT_FOUND);
            }
            LinkedHashMap<String, TrackerStatus> statuses = new LinkedHashMap<>();
            for (TrackerStatus status : jira.projectStatuses(account.get(), canonicalKey.get())) {
                statuses.putIfAbsent(status.id(), status);
            }
            log.info(
                    "Jira project lookup {}: FOUND (Jira answered 200, {} statuses)",
                    canonicalKey.get(),
                    statuses.size());
            return TrackerProjectLookup.found(canonicalKey.get(), List.copyOf(statuses.values()));
        } catch (JiraRejectedException e) {
            Outcome outcome = e.getStatus() == 404 ? Outcome.NOT_FOUND : Outcome.DENIED;
            log.warn("Jira project lookup {}: {} (Jira answered {})", projectKey, outcome, e.getStatus());
            return TrackerProjectLookup.failed(outcome);
        } catch (JiraUnavailableException e) {
            if (e.getStatus() == 0) {
                log.warn("Jira project lookup {}: {} (no answer)", projectKey, Outcome.UNAVAILABLE);
            } else {
                log.warn(
                        "Jira project lookup {}: {} (Jira answered {})",
                        projectKey,
                        Outcome.UNAVAILABLE,
                        e.getStatus());
            }
            return TrackerProjectLookup.failed(Outcome.UNAVAILABLE);
        }
    }
}
