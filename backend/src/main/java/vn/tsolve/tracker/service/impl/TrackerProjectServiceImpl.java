package vn.tsolve.tracker.service.impl;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import vn.tsolve.tracker.client.TicketTrackerClient;
import vn.tsolve.tracker.dto.ChatbotProjectCheck;
import vn.tsolve.tracker.dto.JiraAccount;
import vn.tsolve.tracker.dto.TrackerCreateField;
import vn.tsolve.tracker.dto.TrackerProjectLookup;
import vn.tsolve.tracker.dto.TrackerProjectLookup.Outcome;
import vn.tsolve.tracker.dto.TrackerRole;
import vn.tsolve.tracker.dto.TrackerRoleActors;
import vn.tsolve.tracker.dto.TrackerStatus;
import vn.tsolve.tracker.exception.JiraRejectedException;
import vn.tsolve.tracker.exception.JiraUnavailableException;
import vn.tsolve.tracker.service.JiraServiceAccount;
import vn.tsolve.tracker.service.TrackerProjectService;

@Service
public class TrackerProjectServiceImpl implements TrackerProjectService {

    private static final Logger log = LoggerFactory.getLogger(TrackerProjectServiceImpl.class);

    private static final List<String> CHATBOT_PERMISSIONS = List.of("CREATE_ISSUES", "MODIFY_REPORTER");
    // What the Chatbot issue form (UC-58) will send; any other required field without a default blocks it (K121-D34).
    private static final Set<String> CHATBOT_FIELDS =
            Set.of("summary", "description", "project", "issuetype", "reporter", "labels");

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
        } catch (RuntimeException e) {
            log.warn(
                    "Jira project lookup {}: {} ({})",
                    projectKey,
                    Outcome.UNAVAILABLE,
                    e.getClass().getSimpleName());
            return TrackerProjectLookup.failed(Outcome.UNAVAILABLE);
        }
    }

    @Override
    public ChatbotProjectCheck checkChatbotProject(String projectKey) {
        try {
            Optional<JiraAccount> account = serviceAccount.current();
            if (account.isEmpty()) {
                return logged(projectKey, ChatbotProjectCheck.DENIED, "no service account");
            }
            if (!jira.hasProjectPermissions(account.get(), projectKey, CHATBOT_PERMISSIONS)) {
                return logged(projectKey, ChatbotProjectCheck.DENIED, "permission missing");
            }
            Optional<List<TrackerCreateField>> fields = jira.createFields(account.get(), projectKey, "Task");
            if (fields.isEmpty()) {
                return logged(projectKey, ChatbotProjectCheck.NOT_READY, "no issue type Task");
            }
            boolean blocked = fields.get().stream()
                    .anyMatch(field ->
                            field.required() && !field.hasDefaultValue() && !CHATBOT_FIELDS.contains(field.fieldId()));
            if (blocked) {
                return logged(projectKey, ChatbotProjectCheck.NOT_READY, "another field required");
            }
            log.info("Jira chatbot check {}: {} (Jira answered 200)", projectKey, ChatbotProjectCheck.READY);
            return ChatbotProjectCheck.READY;
        } catch (JiraRejectedException e) {
            return logged(projectKey, ChatbotProjectCheck.DENIED, "Jira answered " + e.getStatus());
        } catch (JiraUnavailableException e) {
            String reason = e.getStatus() == 0 ? "no answer" : "Jira answered " + e.getStatus();
            return logged(projectKey, ChatbotProjectCheck.UNAVAILABLE, reason);
        } catch (RuntimeException e) {
            return logged(
                    projectKey, ChatbotProjectCheck.UNAVAILABLE, e.getClass().getSimpleName());
        }
    }

    @Override
    public Optional<List<TrackerRole>> projectRoles(String projectKey) {
        return readRoles(projectKey, "roles", account -> jira.projectRoles(account, projectKey));
    }

    @Override
    public Optional<TrackerRoleActors> roleActors(String projectKey, long roleId) {
        return readRoles(projectKey, "role " + roleId, account -> jira.roleActors(account, projectKey, roleId));
    }

    // A role read never throws: no token, a refusal or no answer is "not available"; only the status code is logged.
    private <T> Optional<T> readRoles(String projectKey, String what, Function<JiraAccount, T> read) {
        try {
            Optional<JiraAccount> account = serviceAccount.current();
            if (account.isEmpty()) {
                log.warn("Jira {} of {}: not available (no app token)", what, projectKey);
                return Optional.empty();
            }
            return Optional.of(read.apply(account.get()));
        } catch (JiraRejectedException e) {
            log.warn("Jira {} of {}: not available (Jira answered {})", what, projectKey, e.getStatus());
        } catch (JiraUnavailableException e) {
            log.warn(
                    "Jira {} of {}: not available ({})",
                    what,
                    projectKey,
                    e.getStatus() == 0 ? "no answer" : "Jira answered " + e.getStatus());
        } catch (RuntimeException e) {
            log.warn(
                    "Jira {} of {}: not available ({})",
                    what,
                    projectKey,
                    e.getClass().getSimpleName());
        }
        return Optional.empty();
    }

    private static ChatbotProjectCheck logged(String projectKey, ChatbotProjectCheck outcome, String reason) {
        log.warn("Jira chatbot check {}: {} ({})", projectKey, outcome, reason);
        return outcome;
    }
}
