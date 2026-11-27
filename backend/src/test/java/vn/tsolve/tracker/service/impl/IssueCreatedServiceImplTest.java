package vn.tsolve.tracker.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.dao.DataIntegrityViolationException;
import vn.tsolve.common.security.JiraAppPrincipal;
import vn.tsolve.common.tenant.TenantContext;
import vn.tsolve.common.tenant.ThreadLocalTenantContext;
import vn.tsolve.platform.dto.WorkspaceStatus;
import vn.tsolve.platform.dto.WorkspaceSummary;
import vn.tsolve.platform.service.WorkspaceCatalog;
import vn.tsolve.project.service.ProjectService;
import vn.tsolve.tracker.dto.IssueCreatedPayload;
import vn.tsolve.tracker.dto.SuggestionJobRequest;
import vn.tsolve.tracker.entity.SuggestionResult;
import vn.tsolve.tracker.repository.SuggestionResultRepository;
import vn.tsolve.tracker.service.SuggestionJobQueue;

@ExtendWith(MockitoExtension.class)
class IssueCreatedServiceImplTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final UUID PROJECT = UUID.fromString("5b0e7a1c-3f2d-4e8b-9c11-2a7d4f6e8b01");
    private static final UUID SUGGESTION_ID = UUID.fromString("0c9f3e52-6a41-4d7b-8e2f-91b3c5d7a640");
    private static final UUID WORKSPACE = UUID.fromString("7d2a9c41-8b35-4f6e-a1d0-3c5e7f9b2a14");

    private static final IssueCreatedPayload PAYLOAD = payload("VPN drops every hour", adf("Reconnect fails."), null);
    private static final JiraAppPrincipal SITE =
            new JiraAppPrincipal("cloud-1", null, "https://api.atlassian.com/ex/jira/cloud-1");

    @Mock
    private ProjectService projectService;

    @Mock
    private SuggestionResultRepository repository;

    @Mock
    private SuggestionJobQueue jobQueue;

    @Mock
    private WorkspaceCatalog catalog;

    private final TenantContext tenants = new ThreadLocalTenantContext(WORKSPACE::equals);

    @BeforeEach
    void registerTheSite() {
        lenient()
                .when(catalog.findByCloudId("cloud-1"))
                .thenReturn(Optional.of(new WorkspaceSummary(
                        WORKSPACE,
                        "acme",
                        WorkspaceStatus.ACTIVE,
                        "acme.test",
                        Optional.empty(),
                        Optional.empty(),
                        Optional.of("cloud-1"))));
    }

    @Test
    void reachesTheDatabaseAndTheQueueBoundToTheWorkspaceOfTheCallersSite_BUG_403() {
        List<Optional<UUID>> bound = new ArrayList<>();
        given(projectService.findActivePublicIdByJiraProjectKey("ITSUP")).willAnswer(call -> {
            bound.add(tenants.currentWorkspaceId());
            return Optional.of(PROJECT);
        });
        SuggestionResult row = mock(SuggestionResult.class);
        given(row.getId()).willReturn(SUGGESTION_ID);
        given(repository.saveAndFlush(any())).willAnswer(call -> {
            bound.add(tenants.currentWorkspaceId());
            return row;
        });
        willAnswer(call -> {
                    bound.add(tenants.currentWorkspaceId());
                    return null;
                })
                .given(jobQueue)
                .enqueue(any());

        service().receive(SITE, PAYLOAD);

        assertThat(bound).containsExactly(Optional.of(WORKSPACE), Optional.of(WORKSPACE), Optional.of(WORKSPACE));
        assertThat(tenants.currentWorkspaceId()).isEmpty();
    }

    @Test
    void unbindsTheWorkspaceWhenRecordingFails_BUG_403() {
        given(projectService.findActivePublicIdByJiraProjectKey("ITSUP")).willThrow(new IllegalStateException("down"));

        assertThatThrownBy(() -> service().receive(SITE, PAYLOAD)).isInstanceOf(IllegalStateException.class);
        assertThat(tenants.currentWorkspaceId()).isEmpty();
    }

    @Test
    void touchesNothingForASiteWithNoWorkspace_AC_003_9_BUG_403() {
        given(catalog.findByCloudId("cloud-1")).willReturn(Optional.empty());

        service().receive(SITE, PAYLOAD);

        verifyNoInteractions(projectService, repository, jobQueue);
    }

    @Test
    void touchesNothingInAProcessWithNoWorkspaceCatalog_AC_003_9() {
        service(new StaticListableBeanFactory()).receive(SITE, PAYLOAD);

        verifyNoInteractions(projectService, repository, jobQueue);
    }

    // @AuthenticationPrincipal passes null when the principal is not a Jira App one.
    @Test
    void touchesNothingWithoutAJiraAppCaller_BUG_403() {
        service().receive(null, PAYLOAD);

        verifyNoInteractions(catalog, projectService, repository, jobQueue);
    }

    @Test
    void storesSuggestionResultForIssueOfActiveProject_AC_003_1() {
        given(projectService.findActivePublicIdByJiraProjectKey("ITSUP")).willReturn(Optional.of(PROJECT));
        savesRowWithId(SUGGESTION_ID);

        service().receive(SITE, PAYLOAD);

        ArgumentCaptor<SuggestionResult> saved = ArgumentCaptor.forClass(SuggestionResult.class);
        verify(repository).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getProjectId()).isEqualTo(PROJECT);
        assertThat(saved.getValue().getJiraIssueKey()).isEqualTo("ITSUP-1");
        assertThat(saved.getValue().getCreatedAt()).isNotNull();
    }

    @Test
    void acceptsIssueAlreadyStored_AC_003_4() {
        given(projectService.findActivePublicIdByJiraProjectKey("ITSUP")).willReturn(Optional.of(PROJECT));
        given(repository.saveAndFlush(any())).willThrow(violation(IssueCreatedServiceImpl.UNIQUE_ISSUE));

        assertThatCode(() -> service().receive(SITE, PAYLOAD)).doesNotThrowAnyException();
    }

    @Test
    void storesNothingForIssueOfNoActiveProject_AC_003_8() {
        given(projectService.findActivePublicIdByJiraProjectKey("ITSUP")).willReturn(Optional.empty());

        service().receive(SITE, PAYLOAD);

        verifyNoInteractions(repository);
    }

    @Test
    void rethrowsOtherIntegrityErrors_AC_003_1() {
        given(projectService.findActivePublicIdByJiraProjectKey("ITSUP")).willReturn(Optional.of(PROJECT));
        given(repository.saveAndFlush(any())).willThrow(violation("suggestion_result_project_id_fkey"));

        assertThatThrownBy(() -> service().receive(SITE, PAYLOAD)).isInstanceOf(DataIntegrityViolationException.class);
        verifyNoInteractions(jobQueue);
    }

    @Test
    void storesTheIssueTextAndEnqueuesOnlyTheRowIdAfterTheRowIsSaved_AC_011_1() {
        given(projectService.findActivePublicIdByJiraProjectKey("ITSUP")).willReturn(Optional.of(PROJECT));
        savesRowWithId(SUGGESTION_ID);

        service().receive(SITE, PAYLOAD);

        ArgumentCaptor<SuggestionResult> saved = ArgumentCaptor.forClass(SuggestionResult.class);
        InOrder order = inOrder(repository, jobQueue);
        order.verify(repository).saveAndFlush(saved.capture());
        order.verify(jobQueue).enqueue(new SuggestionJobRequest(SUGGESTION_ID, "ITSUP-1"));
        order.verifyNoMoreInteractions();
        assertThat(saved.getValue().getIssueSummary()).isEqualTo("VPN drops every hour");
        assertThat(saved.getValue().getIssueDescription()).isEqualTo("Reconnect fails.");
    }

    @Test
    void storesAnEmptyDescriptionWhenTheIssueHasNone_AC_011_1() {
        given(projectService.findActivePublicIdByJiraProjectKey("ITSUP")).willReturn(Optional.of(PROJECT));
        savesRowWithId(SUGGESTION_ID);

        service().receive(SITE, payload("VPN drops every hour", null, null));

        ArgumentCaptor<SuggestionResult> saved = ArgumentCaptor.forClass(SuggestionResult.class);
        verify(repository).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getIssueDescription()).isEmpty();
        verify(jobQueue).enqueue(new SuggestionJobRequest(SUGGESTION_ID, "ITSUP-1"));
    }

    @Test
    void enqueuesNoJobForAnIssueAlreadyRecorded_AC_011_2() {
        given(projectService.findActivePublicIdByJiraProjectKey("ITSUP")).willReturn(Optional.of(PROJECT));
        given(repository.saveAndFlush(any())).willThrow(violation(IssueCreatedServiceImpl.UNIQUE_ISSUE));

        service().receive(SITE, PAYLOAD);

        verifyNoInteractions(jobQueue);
    }

    @Test
    void enqueuesNoJobForAnIssueOfNoActiveProject_AC_011_2() {
        given(projectService.findActivePublicIdByJiraProjectKey("ITSUP")).willReturn(Optional.empty());

        service().receive(SITE, PAYLOAD);

        verifyNoInteractions(jobQueue);
    }

    @Test
    void enqueuesTheJobOfAnIssueCreatedByTheChatbotLikeAnyOther_AC_011_8() {
        given(projectService.findActivePublicIdByJiraProjectKey("ITSUP")).willReturn(Optional.of(PROJECT));
        savesRowWithId(SUGGESTION_ID);

        service().receive(SITE, payload("VPN drops every hour", adf("Reconnect fails."), List.of("tsolve-chatbot")));

        ArgumentCaptor<SuggestionResult> saved = ArgumentCaptor.forClass(SuggestionResult.class);
        verify(repository).saveAndFlush(saved.capture());
        verify(jobQueue).enqueue(new SuggestionJobRequest(SUGGESTION_ID, "ITSUP-1"));
    }

    // The database gives the row its UUID key on insert; the job is queued with that id.
    private void savesRowWithId(UUID id) {
        SuggestionResult row = mock(SuggestionResult.class);
        given(row.getId()).willReturn(id);
        given(repository.saveAndFlush(any())).willReturn(row);
    }

    private IssueCreatedServiceImpl service() {
        return service(new StaticListableBeanFactory(Map.of("workspaceCatalog", catalog)));
    }

    private IssueCreatedServiceImpl service(StaticListableBeanFactory beans) {
        return new IssueCreatedServiceImpl(
                beans.getBeanProvider(WorkspaceCatalog.class), tenants, projectService, repository, jobQueue);
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
