package vn.tsolve.tracker.service.impl;

import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import vn.tsolve.tracker.client.TicketTrackerClient;
import vn.tsolve.tracker.dto.JiraAccount;
import vn.tsolve.tracker.dto.TrackerComment;
import vn.tsolve.tracker.exception.JiraRejectedException;
import vn.tsolve.tracker.exception.JiraUnavailableException;
import vn.tsolve.tracker.service.JiraServiceAccount;
import vn.tsolve.tracker.service.TrackerIssueService;

@Service
public class TrackerIssueServiceImpl implements TrackerIssueService {

    private static final Logger log = LoggerFactory.getLogger(TrackerIssueServiceImpl.class);

    private final JiraServiceAccount serviceAccount;
    private final TicketTrackerClient jira;

    public TrackerIssueServiceImpl(JiraServiceAccount serviceAccount, TicketTrackerClient jira) {
        this.serviceAccount = serviceAccount;
        this.jira = jira;
    }

    @Override
    public List<String> comments(String issueKey) {
        Optional<JiraAccount> account = serviceAccount.current();
        if (account.isEmpty()) {
            log.info("Issue comments not read for {}: no Jira service account", issueKey);
            return List.of();
        }
        try {
            return jira.comments(account.get(), issueKey).stream()
                    .map(TrackerComment::text)
                    .filter(text -> text != null && !text.isBlank())
                    .toList();
        } catch (JiraRejectedException e) {
            log.info("Issue comments not read for {}: Jira answered {}", issueKey, e.getStatus());
        } catch (JiraUnavailableException e) {
            log.info(
                    "Issue comments not read for {}: Jira answered {}",
                    issueKey,
                    e.getStatus() == 0 ? "nothing" : e.getStatus());
        } catch (RuntimeException e) {
            log.warn(
                    "Issue comments not read for {}: {}", issueKey, e.getClass().getSimpleName());
        }
        return List.of();
    }
}
