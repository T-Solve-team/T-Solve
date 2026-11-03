package vn.tsolve.tracker.service.impl;

import java.time.Instant;
import java.util.Optional;
import org.hibernate.exception.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import vn.tsolve.project.service.ProjectService;
import vn.tsolve.tracker.dto.IssueCreatedPayload;
import vn.tsolve.tracker.entity.SuggestionResult;
import vn.tsolve.tracker.repository.SuggestionResultRepository;
import vn.tsolve.tracker.service.IssueCreatedService;

@Service
public class IssueCreatedServiceImpl implements IssueCreatedService {

    static final String UNIQUE_ISSUE = "uq_suggestion_issue";

    private static final Logger log = LoggerFactory.getLogger(IssueCreatedServiceImpl.class);

    private final ProjectService projectService;
    private final SuggestionResultRepository suggestionResultRepository;

    public IssueCreatedServiceImpl(
            ProjectService projectService, SuggestionResultRepository suggestionResultRepository) {
        this.projectService = projectService;
        this.suggestionResultRepository = suggestionResultRepository;
    }

    // Not @Transactional: the insert runs in the repository's own transaction, so a duplicate rolls back only
    // that insert and can still be answered 202 (edge cases 11, 12).
    @Override
    public void receive(IssueCreatedPayload payload) {
        Optional<Long> projectId = projectService.findActiveIdByJiraProjectKey(payload.projectKey());
        if (projectId.isEmpty()) {
            log.info("Issue created outside T-Solve Projects: {} {}", payload.issueKey(), payload.projectKey());
            return;
        }
        try {
            suggestionResultRepository.saveAndFlush(
                    new SuggestionResult(projectId.get(), payload.issueKey(), Instant.now()));
            log.info("Issue created recorded: {}", payload.issueKey());
        } catch (DataIntegrityViolationException e) {
            if (!isDuplicate(e)) {
                throw e;
            }
            log.info("Issue created already recorded: {}", payload.issueKey());
        }
    }

    private static boolean isDuplicate(DataIntegrityViolationException e) {
        return e.getCause() instanceof ConstraintViolationException violation
                && UNIQUE_ISSUE.equalsIgnoreCase(violation.getConstraintName());
    }
}
