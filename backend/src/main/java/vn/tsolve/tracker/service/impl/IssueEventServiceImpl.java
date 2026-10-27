package vn.tsolve.tracker.service.impl;

import java.time.Instant;
import org.hibernate.exception.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import vn.tsolve.tracker.dto.IssueEventPayload;
import vn.tsolve.tracker.entity.IssueEvent;
import vn.tsolve.tracker.entity.IssueEventType;
import vn.tsolve.tracker.repository.IssueEventRepository;
import vn.tsolve.tracker.service.IssueEventService;

@Service
public class IssueEventServiceImpl implements IssueEventService {

    static final String UNIQUE_EVENT = "uq_issue_event";

    private static final Logger log = LoggerFactory.getLogger(IssueEventServiceImpl.class);

    private final IssueEventRepository issueEventRepository;
    private final TokenCipher tokenCipher;

    public IssueEventServiceImpl(IssueEventRepository issueEventRepository, TokenCipher tokenCipher) {
        this.issueEventRepository = issueEventRepository;
        this.tokenCipher = tokenCipher;
    }

    // Not @Transactional: the insert is the only write and runs in the repository's own transaction, so a
    // duplicate rolls back only that insert and can be answered 202 (edge cases 11, 12).
    @Override
    public void receive(String cloudId, IssueEventType eventType, IssueEventPayload payload, String systemToken) {
        IssueEvent event = new IssueEvent(
                cloudId,
                payload.issue().id(),
                payload.issue().key(),
                eventType,
                payload.timestampMillis(),
                tokenCipher.encrypt(systemToken),
                Instant.now());
        try {
            issueEventRepository.saveAndFlush(event);
            log.info(
                    "Issue event recorded: {} {} {}", eventType, payload.issue().key(), cloudId);
        } catch (DataIntegrityViolationException e) {
            if (!isDuplicate(e)) {
                throw e;
            }
            log.info(
                    "Issue event already recorded: {} {} {}",
                    eventType,
                    payload.issue().key(),
                    cloudId);
        }
    }

    private static boolean isDuplicate(DataIntegrityViolationException e) {
        return e.getCause() instanceof ConstraintViolationException violation
                && UNIQUE_EVENT.equalsIgnoreCase(violation.getConstraintName());
    }
}
