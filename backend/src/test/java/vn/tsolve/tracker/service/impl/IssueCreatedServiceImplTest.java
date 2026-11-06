package vn.tsolve.tracker.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import vn.tsolve.project.service.ProjectService;
import vn.tsolve.tracker.dto.IssueCreatedPayload;
import vn.tsolve.tracker.dto.SuggestionJobRequest;
import vn.tsolve.tracker.entity.SuggestionResult;
import vn.tsolve.tracker.repository.SuggestionResultRepository;
import vn.tsolve.tracker.service.SuggestionJobQueue;

@ExtendWith(MockitoExtension.class)
class IssueCreatedServiceImplTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    private static final IssueCreatedPayload PAYLOAD = payload("VPN drops every hour", adf("Reconnect fails."), null);

    @Mock
    private ProjectService projectService;

    @Mock
    private SuggestionResultRepository repository;

    @Mock
    private SuggestionJobQueue jobQueue;

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
        verifyNoInteractions(jobQueue);
    }

    @Test
    void enqueuesOneJobWithTheIssueTextAfterTheRowIsSaved_AC_011_1() {
        given(projectService.findActiveIdByJiraProjectKey("ITSUP")).willReturn(Optional.of(4L));

        service().receive(PAYLOAD);

        InOrder order = inOrder(repository, jobQueue);
        order.verify(repository).saveAndFlush(any());
        order.verify(jobQueue)
                .enqueue(new SuggestionJobRequest("ITSUP-1", 4L, "VPN drops every hour", "Reconnect fails."));
        order.verifyNoMoreInteractions();
    }

    @Test
    void enqueuesAnEmptyDescriptionWhenTheIssueHasNone_AC_011_1() {
        given(projectService.findActiveIdByJiraProjectKey("ITSUP")).willReturn(Optional.of(4L));

        service().receive(payload("VPN drops every hour", null, null));

        verify(jobQueue).enqueue(new SuggestionJobRequest("ITSUP-1", 4L, "VPN drops every hour", ""));
    }

    @Test
    void enqueuesNoJobForAnIssueAlreadyRecorded_AC_011_2() {
        given(projectService.findActiveIdByJiraProjectKey("ITSUP")).willReturn(Optional.of(4L));
        given(repository.saveAndFlush(any())).willThrow(violation(IssueCreatedServiceImpl.UNIQUE_ISSUE));

        service().receive(PAYLOAD);

        verifyNoInteractions(jobQueue);
    }

    @Test
    void enqueuesNoJobForAnIssueOfNoActiveProject_AC_011_2() {
        given(projectService.findActiveIdByJiraProjectKey("ITSUP")).willReturn(Optional.empty());

        service().receive(PAYLOAD);

        verifyNoInteractions(jobQueue);
    }

    @Test
    void enqueuesTheJobOfAnIssueCreatedByTheChatbotLikeAnyOther_AC_011_8() {
        given(projectService.findActiveIdByJiraProjectKey("ITSUP")).willReturn(Optional.of(4L));

        service().receive(payload("VPN drops every hour", adf("Reconnect fails."), List.of("tsolve-chatbot")));

        verify(repository).saveAndFlush(any());
        verify(jobQueue).enqueue(new SuggestionJobRequest("ITSUP-1", 4L, "VPN drops every hour", "Reconnect fails."));
    }

    private IssueCreatedServiceImpl service() {
        return new IssueCreatedServiceImpl(projectService, repository, jobQueue);
    }

    private static IssueCreatedPayload payload(String summary, JsonNode description, List<String> labels) {
        return new IssueCreatedPayload(new IssueCreatedPayload.Issue(
                "ITSUP-1",
                new IssueCreatedPayload.Fields(
                        new IssueCreatedPayload.Project("ITSUP"), summary, description, labels)));
    }

    private static JsonNode adf(String text) {
        return JSON.createObjectNode()
                .put("type", "doc")
                .put("version", 1)
                .set(
                        "content",
                        JSON.createArrayNode()
                                .add(JSON.createObjectNode()
                                        .put("type", "paragraph")
                                        .set(
                                                "content",
                                                JSON.createArrayNode()
                                                        .add(JSON.createObjectNode()
                                                                .put("type", "text")
                                                                .put("text", text)))));
    }

    private static DataIntegrityViolationException violation(String constraint) {
        return new DataIntegrityViolationException(
                "insert failed", new ConstraintViolationException("insert failed", new SQLException(), constraint));
    }
}
