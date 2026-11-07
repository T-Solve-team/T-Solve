package vn.tsolve.tracker.service.impl;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.hibernate.exception.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import vn.tsolve.project.service.ProjectService;
import vn.tsolve.tracker.client.AdfTextExtractor;
import vn.tsolve.tracker.dto.IssueCreatedPayload;
import vn.tsolve.tracker.dto.SuggestionJobRequest;
import vn.tsolve.tracker.entity.SuggestionResult;
import vn.tsolve.tracker.repository.SuggestionResultRepository;
import vn.tsolve.tracker.service.IssueCreatedService;
import vn.tsolve.tracker.service.SuggestionJobQueue;

@Service
public class IssueCreatedServiceImpl implements IssueCreatedService {

    static final String UNIQUE_ISSUE = "uq_suggestion_issue";

    private static final Logger log = LoggerFactory.getLogger(IssueCreatedServiceImpl.class);

    private final ProjectService projectService;
    private final SuggestionResultRepository suggestionResultRepository;
    private final SuggestionJobQueue suggestionJobQueue;

    public IssueCreatedServiceImpl(
            ProjectService projectService,
            SuggestionResultRepository suggestionResultRepository,
            SuggestionJobQueue suggestionJobQueue) {
        this.projectService = projectService;
        this.suggestionResultRepository = suggestionResultRepository;
        this.suggestionJobQueue = suggestionJobQueue;
    }

    // Not @Transactional: the insert runs in the repository's own transaction, so a duplicate rolls back only
    // that insert and can still be answered 202 (edge cases 11, 12), and the row is committed before the job is
    // queued (AC-011.1).
    @Override
    public void receive(IssueCreatedPayload payload) {
        Optional<UUID> projectId = projectService.findActivePublicIdByJiraProjectKey(payload.projectKey());
        if (projectId.isEmpty()) {
            log.info("Issue created outside T-Solve Projects: {} {}", payload.issueKey(), payload.projectKey());
            return;
        }
        // The issue text is kept in the row; the job carries only the row's id (AC-011.1).
        SuggestionResult result = new SuggestionResult(
                projectId.get(),
                payload.issueKey(),
                payload.summary(),
                AdfTextExtractor.toText(payload.description()),
                Instant.now());
        SuggestionResult saved;
        try {
            saved = suggestionResultRepository.saveAndFlush(result);
            log.info("Issue created recorded: {}", payload.issueKey());
        } catch (DataIntegrityViolationException e) {
            if (!isDuplicate(e)) {
                throw e;
            }
            log.info("Issue created already recorded: {}", payload.issueKey());
            return;
        }
        // Labels are not looked at: an issue created by the Chatbot is suggested like any other (AC-011.8).
        suggestionJobQueue.enqueue(new SuggestionJobRequest(saved.getId(), payload.issueKey()));
    }

    private static boolean isDuplicate(DataIntegrityViolationException e) {
        return e.getCause() instanceof ConstraintViolationException violation
                && UNIQUE_ISSUE.equalsIgnoreCase(violation.getConstraintName());
    }
}
