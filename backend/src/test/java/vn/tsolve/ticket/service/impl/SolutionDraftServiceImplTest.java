package vn.tsolve.ticket.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import vn.tsolve.ticket.dto.SolutionSections;
import vn.tsolve.ticket.entity.SolutionDraft;
import vn.tsolve.ticket.repository.SolutionDraftRepository;

class SolutionDraftServiceImplTest {

    private static final UUID CREATOR = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID OTHER = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID PROJECT = UUID.fromString("00000000-0000-0000-0000-000000000126");
    private static final String ISSUE = "ITSUP-12";
    private static final Instant NOW = Instant.parse("2026-11-11T03:00:00Z");

    private final SolutionDraftRepository drafts = mock(SolutionDraftRepository.class);
    private final SolutionDraftServiceImpl service =
            new SolutionDraftServiceImpl(drafts, Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void storesAFirstDraftUnvalidated_AC_005_1() {
        given(drafts.findByCreatorIdAndProjectIdAndIssueKey(CREATOR, PROJECT, ISSUE))
                .willReturn(Optional.empty());
        SolutionSections unfinished = new SolutionSections("Drops", "", "", "", "");

        service.save(CREATOR, PROJECT, ISSUE, unfinished);

        ArgumentCaptor<SolutionDraft> saved = ArgumentCaptor.forClass(SolutionDraft.class);
        verify(drafts).save(saved.capture());
        assertThat(saved.getValue().getCreatorId()).isEqualTo(CREATOR);
        assertThat(saved.getValue().getContent()).isEqualTo(unfinished);
    }

    @Test
    void replacesTheOnlyDraftOfTheIssue_AC_005_1() {
        SolutionDraft existing =
                new SolutionDraft(CREATOR, PROJECT, ISSUE, new SolutionSections("Old", "", "", "", ""), NOW);
        given(drafts.findByCreatorIdAndProjectIdAndIssueKey(CREATOR, PROJECT, ISSUE))
                .willReturn(Optional.of(existing));
        SolutionSections newer = new SolutionSections("New", "Cause", "", "", "");

        service.save(CREATOR, PROJECT, ISSUE, newer);

        assertThat(existing.getContent()).isEqualTo(newer);
        verify(drafts, never()).save(any());
    }

    @Test
    void returnsOnlyTheCreatorsOwnDraft_AC_005_3() {
        SolutionDraft mine =
                new SolutionDraft(CREATOR, PROJECT, ISSUE, new SolutionSections("Mine", "", "", "", ""), NOW);
        given(drafts.findByCreatorIdAndProjectIdAndIssueKey(CREATOR, PROJECT, ISSUE))
                .willReturn(Optional.of(mine));
        given(drafts.findByCreatorIdAndProjectIdAndIssueKey(OTHER, PROJECT, ISSUE))
                .willReturn(Optional.empty());

        assertThat(service.find(CREATOR, PROJECT, ISSUE)).contains(mine.getContent());
        assertThat(service.find(OTHER, PROJECT, ISSUE)).isEmpty();
    }
}
