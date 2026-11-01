package vn.tsolve.tracker.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.sql.SQLException;
import java.util.Optional;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import vn.tsolve.project.service.ProjectService;
import vn.tsolve.tracker.dto.IssueCreatedPayload;
import vn.tsolve.tracker.entity.SuggestionResult;
import vn.tsolve.tracker.repository.SuggestionResultRepository;

@ExtendWith(MockitoExtension.class)
class IssueCreatedServiceImplTest {

    private static final IssueCreatedPayload PAYLOAD = new IssueCreatedPayload(new IssueCreatedPayload.Issue(
            "ITSUP-1", new IssueCreatedPayload.Fields(new IssueCreatedPayload.Project("ITSUP"))));

    @Mock
    private ProjectService projectService;

    @Mock
    private SuggestionResultRepository repository;

    @Test
    void storesSuggestionResultForIssueOfActiveProject_AC_003_1() {
        given(projectService.findActiveIdByJiraProjectKey("ITSUP")).willReturn(Optional.of(4L));

        service().receive(PAYLOAD);

        ArgumentCaptor<SuggestionResult> saved = ArgumentCaptor.forClass(SuggestionResult.class);
        verify(repository).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getProjectId()).isEqualTo(4L);
        assertThat(saved.getValue().getJiraIssueKey()).isEqualTo("ITSUP-1");
        assertThat(saved.getValue().getCreatedAt()).isNotNull();
    }

    @Test
    void acceptsIssueAlreadyStored_AC_003_4() {
        given(projectService.findActiveIdByJiraProjectKey("ITSUP")).willReturn(Optional.of(4L));
        given(repository.saveAndFlush(any())).willThrow(violation(IssueCreatedServiceImpl.UNIQUE_ISSUE));

        assertThatCode(() -> service().receive(PAYLOAD)).doesNotThrowAnyException();
    }

    @Test
    void storesNothingForIssueOfNoActiveProject_AC_003_8() {
        given(projectService.findActiveIdByJiraProjectKey("ITSUP")).willReturn(Optional.empty());

        service().receive(PAYLOAD);

        verifyNoInteractions(repository);
    }

    @Test
    void rethrowsOtherIntegrityErrors_AC_003_1() {
        given(projectService.findActiveIdByJiraProjectKey("ITSUP")).willReturn(Optional.of(4L));
        given(repository.saveAndFlush(any())).willThrow(violation("suggestion_result_project_id_fkey"));

        assertThatThrownBy(() -> service().receive(PAYLOAD)).isInstanceOf(DataIntegrityViolationException.class);
    }

    private IssueCreatedServiceImpl service() {
        return new IssueCreatedServiceImpl(projectService, repository);
    }

    private static DataIntegrityViolationException violation(String constraint) {
        return new DataIntegrityViolationException(
                "insert failed", new ConstraintViolationException("insert failed", new SQLException(), constraint));
    }
}
