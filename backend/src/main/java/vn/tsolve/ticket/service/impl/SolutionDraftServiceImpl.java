package vn.tsolve.ticket.service.impl;

import java.time.Clock;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.tsolve.ticket.dto.SolutionSections;
import vn.tsolve.ticket.entity.SolutionDraft;
import vn.tsolve.ticket.repository.SolutionDraftRepository;
import vn.tsolve.ticket.service.SolutionDraftService;

@Service
public class SolutionDraftServiceImpl implements SolutionDraftService {

    private final SolutionDraftRepository drafts;
    private final Clock clock;

    public SolutionDraftServiceImpl(SolutionDraftRepository drafts, Clock clock) {
        this.drafts = drafts;
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
}
