package vn.tsolve.ticket.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.tsolve.ticket.dto.IssueStatusChange;
import vn.tsolve.ticket.dto.OpenDraft;
import vn.tsolve.ticket.dto.SolutionSections;
import vn.tsolve.ticket.entity.JiraIssueDoneState;
import vn.tsolve.ticket.entity.SolutionDraft;
import vn.tsolve.ticket.repository.JiraIssueDoneStateRepository;
import vn.tsolve.ticket.repository.SolutionDraftRepository;
import vn.tsolve.ticket.service.SolutionDraftService;

@Service
public class SolutionDraftServiceImpl implements SolutionDraftService {

    private final SolutionDraftRepository drafts;
    private final JiraIssueDoneStateRepository doneStates;
    private final ObjectMapper json;
    private final Clock clock;

    public SolutionDraftServiceImpl(
            SolutionDraftRepository drafts, JiraIssueDoneStateRepository doneStates, ObjectMapper json, Clock clock) {
        this.drafts = drafts;
        this.doneStates = doneStates;
        this.json = json;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void save(UUID creatorId, UUID projectId, String issueKey, SolutionSections sections) {
        drafts.findByCreatorIdAndProjectIdAndIssueKey(creatorId, projectId, issueKey)
                .ifPresentOrElse(
                        draft -> draft.replace(sections, clock.instant()),
                        () -> drafts.save(
                                new SolutionDraft(creatorId, projectId, issueKey, sections, clock.instant())));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<SolutionSections> find(UUID creatorId, UUID projectId, String issueKey) {
        return drafts.findByCreatorIdAndProjectIdAndIssueKey(creatorId, projectId, issueKey)
                .map(SolutionDraft::getContent);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Boolean> findIssueDone(UUID projectId, String issueKey) {
        return doneStates.findByProjectIdAndIssueKey(projectId, issueKey).map(JiraIssueDoneState::isDone);
    }

    @Override
    @Transactional
    public boolean recordIssueStatus(IssueStatusChange change) {
        Instant now = clock.instant();
        doneStates.record(change.projectId(), change.issueKey(), change.done(), now);
        return change.draftOwner() != null
                && drafts.insertIfAbsent(
                                change.draftOwner(),
                                change.projectId(),
                                change.issueKey(),
                                content(change.draftSections()),
                                now)
                        == 1;
    }

    @Override
    @Transactional
    public boolean replaceUnsavedDraft(UUID creatorId, UUID projectId, String issueKey, SolutionSections sections) {
        return drafts.replaceIfUnsaved(creatorId, projectId, issueKey, content(sections), clock.instant()) == 1;
    }

    private String content(SolutionSections sections) {
        try {
            return json.writeValueAsString(sections);
        } catch (JsonProcessingException e) {
            // Five strings always serialize; no section text goes into the message (NF 3).
            throw new IllegalStateException("Draft content could not be written", null);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<OpenDraft> findOpenDrafts() {
        return drafts.findOpenDrafts();
    }

    @Override
    @Transactional(readOnly = true)
    public List<OpenDraft> findOpenDrafts(UUID creatorId) {
        return drafts.findOpenDrafts(creatorId);
    }
}
