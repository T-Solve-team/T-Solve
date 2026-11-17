package vn.tsolve.workspace.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.persistence.EntityManager;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mapstruct.factory.Mappers;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import vn.tsolve.common.audit.AuditAction;
import vn.tsolve.common.audit.AuditService;
import vn.tsolve.common.exception.BusinessRuleException;
import vn.tsolve.common.exception.FieldMessageCodeException;
import vn.tsolve.common.exception.NotFoundException;
import vn.tsolve.identity.signin.port.TenantScope;
import vn.tsolve.workspace.dto.JiraAppToken;
import vn.tsolve.workspace.dto.WorkspaceInfoMapper;
import vn.tsolve.workspace.dto.WorkspaceInfoResponse;
import vn.tsolve.workspace.dto.WorkspaceSetupRequest;
import vn.tsolve.workspace.dto.WorkspaceUpdateRequest;
import vn.tsolve.workspace.entity.WorkspaceInfo;
import vn.tsolve.workspace.repository.WorkspaceInfoRepository;
import vn.tsolve.workspace.service.JiraAppTokenService;
import vn.tsolve.workspace.site.JiraSiteLinkRefusedException;
import vn.tsolve.workspace.site.JiraSiteLinkRefusedException.Reason;
import vn.tsolve.workspace.site.JiraSiteLinker;

class WorkspaceServiceImplTest {
    private static final String SITE = "https://fake.atlassian.net";
    private static final String CLOUD_ID = "1324a887-45db-1bf4-1e99-ef0ff456d421";
    private static final Instant RECEIVED = Instant.parse("2026-11-17T03:00:00Z");
    private final ObjectMapper json = new ObjectMapper().findAndRegisterModules();
    private final UUID workspaceId = UUID.randomUUID();
    private final TenantScope scope = mock(TenantScope.class);
    private final WorkspaceInfoRepository repository = mock(WorkspaceInfoRepository.class);
    private final EntityManager entities = mock(EntityManager.class);
    private final JiraSiteLinker linker = mock(JiraSiteLinker.class);
    private final JiraAppTokenService tokens = mock(JiraAppTokenService.class);
    private final AuditService audit = mock(AuditService.class);
    private final PlatformTransactionManager transactions = mock(PlatformTransactionManager.class);
    private WorkspaceServiceImpl service;

    @BeforeEach
    void setup() {
        when(scope.currentWorkspaceId()).thenReturn(workspaceId);
        when(linker.readCloudId(SITE)).thenReturn(CLOUD_ID);
        when(tokens.current(any())).thenReturn(Optional.empty());
        service = new WorkspaceServiceImpl(
                scope,
                repository,
                entities,
                linker,
                tokens,
                audit,
                Mappers.getMapper(WorkspaceInfoMapper.class),
                transactions);
    }

    private ObjectNode valid() {
        return json.createObjectNode()
                .put("companyName", "Fake company")
                .put("jiraSiteUrl", " HTTPS://FAKE.ATLASSIAN.NET/ ");
    }

    private void error(JsonNode input, String field, String code, Map<String, Object> params) {
        assertThatThrownBy(() -> service.setup(input)).isInstanceOfSatisfying(FieldMessageCodeException.class, e -> {
            assertThat(e.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(e.getField()).isEqualTo(field);
            assertThat(e.getCode()).isEqualTo(code);
            assertThat(e.getParams()).isEqualTo(params);
            assertThat(e.getCause()).isNull();
        });
        verifyNoInteractions(entities, audit);
    }

    @Test
    @DisplayName("AC-005.1 inserts bound id, GOOGLE, normalized site and its cloud id, no Jira credential")
    void insertsSetup() {
        WorkspaceInfoResponse response = service.setup(valid());
        var saved = ArgumentCaptor.forClass(WorkspaceInfo.class);
        verify(entities).persist(saved.capture());
        verify(entities).flush();
        WorkspaceInfo row = saved.getValue();
        assertThat(row.getId()).isEqualTo(workspaceId);
        assertThat(row.getCompanyName()).isEqualTo("Fake company");
        assertThat(row.getJiraSiteAddress()).isEqualTo(SITE);
        assertThat(row.getJiraCloudId()).isEqualTo(CLOUD_ID);
        assertThat(row.getSignInProvider()).isEqualTo("GOOGLE");
        assertThat(row.getTokenExpiryDate()).isNull();
        assertThat(response).isEqualTo(new WorkspaceInfoResponse("Fake company", SITE, false, null));
    }

    @Test
    @DisplayName("AC-005.15 ignores a service account email and token sent by an old client")
    void ignoresServiceAccountFields() {
        service.setup(valid().put("serviceAccountEmail", "not an email").put("serviceAccountToken", "obviously-fake"));
        var saved = ArgumentCaptor.forClass(WorkspaceInfo.class);
        verify(entities).persist(saved.capture());
        assertThat(saved.getValue().toString()).doesNotContain("obviously-fake");
    }

    @ParameterizedTest
    @CsvSource({"companyName,Company name", "jiraSiteUrl,Jira site address"})
    @DisplayName("AC-005.2 rejects missing, null, blank and non-string required fields before any call")
    void requiresFields(String field, String label) {
        ObjectNode input = valid();
        input.remove(field);
        error(input, field, "MSG01", Map.of("field_name", label));
        input.putNull(field);
        error(input, field, "MSG01", Map.of("field_name", label));
        input.put(field, " \t\n ");
        error(input, field, "MSG01", Map.of("field_name", label));
        input.put(field, 42);
        error(input, field, "MSG01", Map.of("field_name", label));
        verifyNoInteractions(repository, linker);
    }

    @ParameterizedTest
    @CsvSource({"companyName,Company name,100", "jiraSiteUrl,Jira site address,200"})
    @DisplayName("AC-005.3 measures raw length and returns MSG02 with field_name and max_length")
    void limitsFields(String field, String label, int limit) {
        error(
                valid().put(field, "x".repeat(limit + 1)),
                field,
                "MSG02",
                Map.of("field_name", label, "max_length", limit));
        verifyNoInteractions(repository, linker);
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "https://localhost",
                "http://fake.atlassian.net",
                "https://169.254.169.254",
                "https://fake.atlassian.net.evil.test",
                "https://fake.atlassian.net/path"
            })
    @DisplayName("AC-005.5 rejects unsafe sites before checking the row or making outbound calls")
    void rejectsSite(String site) {
        error(valid().put("jiraSiteUrl", site), "jiraSiteUrl", "MSG61", Map.of());
        verifyNoInteractions(repository, linker);
    }

    @Test
    @DisplayName("AC-005.6 AC-005.8 reads the cloud id outside the transaction, then links, inserts and audits in it")
    void readsCloudIdThenLinksInOneTransaction() {
        when(transactions.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        service.setup(valid());
        var order = inOrder(repository, linker, transactions, entities, audit);
        order.verify(repository).count();
        order.verify(linker).readCloudId(SITE);
        order.verify(transactions).getTransaction(any());
        order.verify(linker).linkJiraSite(SITE);
        order.verify(entities).persist(any());
        order.verify(entities).flush();
        order.verify(audit)
                .record(
                        AuditAction.TRACKER_CONFIGURATION_CHANGE,
                        "WORKSPACE",
                        workspaceId,
                        Map.of("companyName", "Fake company", "jiraSiteUrl", SITE));
        order.verify(transactions).commit(any());
    }

    @Test
    @DisplayName("AC-005.8 a refused link rolls the setup transaction back")
    void rollsBackRefusedLink() {
        when(transactions.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        when(linker.linkJiraSite(SITE)).thenThrow(new JiraSiteLinkRefusedException(Reason.LINKED_ELSEWHERE));
        assertThatThrownBy(() -> service.setup(valid())).isInstanceOf(FieldMessageCodeException.class);
        verify(transactions).rollback(any());
        verify(transactions, never()).commit(any());
    }

    @Test
    @DisplayName("AC-005.9 linker refusal for another Workspace returns MSG153 without inserting")
    void rejectsLinkedElsewhere() {
        when(linker.linkJiraSite(SITE)).thenThrow(new JiraSiteLinkRefusedException(Reason.LINKED_ELSEWHERE));
        error(valid(), "jiraSiteUrl", "MSG153", Map.of("tracker_name", "Jira"));
    }

    @Test
    @DisplayName("AC-005.10 unreadable tenant info at link time returns MSG61")
    void rejectsUnreadableLink() {
        when(linker.linkJiraSite(SITE)).thenThrow(new JiraSiteLinkRefusedException(Reason.UNREADABLE));
        error(valid(), "jiraSiteUrl", "MSG61", Map.of());
    }

    @ParameterizedTest
    @ValueSource(strings = {"UNREADABLE", "LINKED_ELSEWHERE"})
    @DisplayName("AC-005.14 an unreadable cloud id returns MSG61 without a link or a row")
    void rejectsUnreadableCloudId(String reason) {
        when(linker.readCloudId(SITE)).thenThrow(new JiraSiteLinkRefusedException(Reason.valueOf(reason)));
        error(valid(), "jiraSiteUrl", "MSG61", Map.of());
        verify(linker, never()).linkJiraSite(any());
    }

    @Test
    @DisplayName("AC-005.11 an existing row returns WORKSPACE_ALREADY_SET_UP before external calls")
    void rejectsExisting() {
        when(repository.count()).thenReturn(1L);
        assertThatThrownBy(() -> service.setup(valid()))
                .isInstanceOfSatisfying(BusinessRuleException.class, e -> assertThat(e.getCode())
                        .isEqualTo("WORKSPACE_ALREADY_SET_UP"));
        verifyNoInteractions(linker, entities, audit);
    }

    @ParameterizedTest
    @ValueSource(strings = {"uq_workspace_singleton", "pk_workspace_info"})
    @DisplayName("AC-005.11 concurrent singleton or primary key insert violation returns a safe conflict")
    void rejectsConcurrentInsert(String constraint) {
        doThrow(new ConstraintViolationException(
                        "fake database error", new SQLException("fake database error", "23505"), constraint))
                .when(entities)
                .flush();
        assertThatThrownBy(() -> service.setup(valid())).isInstanceOfSatisfying(BusinessRuleException.class, e -> {
            assertThat(e.getCode()).isEqualTo("WORKSPACE_ALREADY_SET_UP");
            assertThat(e.getCause()).isNull();
        });
        verifyNoInteractions(audit);
    }

    @Test
    @DisplayName("AC-005.11 unrelated integrity failures are not mislabeled as already set up")
    void preservesOtherFailure() {
        var failure = new ConstraintViolationException(
                "fake database error", new SQLException("check", "23514"), "ck_workspace_singleton");
        doThrow(failure).when(entities).flush();
        assertThatThrownBy(() -> service.setup(valid())).isSameAs(failure);
        verifyNoInteractions(audit);
    }

    @ParameterizedTest
    @ValueSource(strings = {"null", "[]", "42", "{} {}", "{", ""})
    @DisplayName("AC-005.2 malformed or non-object request bodies return safe MSG03")
    void rejectsMalformedBody(String input) {
        assertThatThrownBy(() -> WorkspaceSetupRequest.parse(input, json))
                .isInstanceOfSatisfying(FieldMessageCodeException.class, e -> {
                    assertThat(e.getCode()).isEqualTo("MSG03");
                    assertThat(e.getCause()).isNull();
                });
    }

    @Test
    @DisplayName("AC-006.2 AC-006.6 reads the public fields and the Jira App status, no token and no cloud id")
    void readsWorkspaceWithJiraAppStatus() throws Exception {
        when(repository.findById(workspaceId))
                .thenReturn(Optional.of(new WorkspaceInfo(workspaceId, "Fake company", SITE, CLOUD_ID)));
        when(tokens.current(workspaceId))
                .thenReturn(Optional.of(
                        new JiraAppToken("obviously-fake-app-token", CLOUD_ID, RECEIVED, RECEIVED.plusSeconds(3600))));
        WorkspaceInfoResponse response = service.get();
        assertThat(response).isEqualTo(new WorkspaceInfoResponse("Fake company", SITE, true, RECEIVED));
        assertThat(json.writeValueAsString(response)).doesNotContain("obviously-fake-app-token", CLOUD_ID);
    }

    @Test
    @DisplayName("AC-006.6 reports not connected while no app token is held")
    void readsWorkspaceNotConnected() {
        when(repository.findById(workspaceId))
                .thenReturn(Optional.of(new WorkspaceInfo(workspaceId, "Fake company", SITE, CLOUD_ID)));
        assertThat(service.get()).isEqualTo(new WorkspaceInfoResponse("Fake company", SITE, false, null));
    }

    @Test
    @DisplayName("AC-006.3 absent Workspace information returns MSG60")
    void rejectsMissingWorkspace() {
        assertThatThrownBy(service::get).isInstanceOfSatisfying(NotFoundException.class, e -> assertThat(e.getCode())
                .isEqualTo("MSG60"));
        verifyNoInteractions(linker, entities, audit);
    }

    private WorkspaceInfo storedWorkspace() {
        var stored = new WorkspaceInfo(workspaceId, "Fake company", SITE, CLOUD_ID);
        when(repository.findById(workspaceId)).thenReturn(Optional.of(stored));
        when(repository.save(any(WorkspaceInfo.class))).thenAnswer(call -> call.getArgument(0));
        when(transactions.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        return stored;
    }

    private void updateError(JsonNode input, String field, String code, Map<String, Object> params) {
        assertThatThrownBy(() -> service.update(input)).isInstanceOfSatisfying(FieldMessageCodeException.class, e -> {
            assertThat(e.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(e.getField()).isEqualTo(field);
            assertThat(e.getCode()).isEqualTo(code);
            assertThat(e.getParams()).isEqualTo(params);
            assertThat(e.getCause()).isNull();
        });
        verify(repository, never()).save(any());
        verifyNoInteractions(entities, audit, transactions);
    }

    @Test
    @DisplayName("AC-007.1 AC-007.6 AC-007.8 renames without calling Jira and audits only the company name")
    void renamesWithoutJira() {
        storedWorkspace();
        WorkspaceInfoResponse response = service.update(json.createObjectNode().put("companyName", " Renamed "));
        var saved = ArgumentCaptor.forClass(WorkspaceInfo.class);
        verify(repository).save(saved.capture());
        assertThat(saved.getValue().getCompanyName()).isEqualTo("Renamed");
        assertThat(saved.getValue().getJiraCloudId()).isEqualTo(CLOUD_ID);
        assertThat(response).isEqualTo(new WorkspaceInfoResponse("Renamed", SITE, false, null));
        verify(audit)
                .record(
                        AuditAction.TRACKER_CONFIGURATION_CHANGE,
                        "WORKSPACE",
                        workspaceId,
                        Map.of("companyName", Map.of("old", "Fake company", "new", "Renamed")));
        verifyNoInteractions(linker);
        verify(transactions).commit(any());
    }

    @Test
    @DisplayName("AC-007.7 ignores jiraSiteUrl, serviceAccountEmail and serviceAccountToken, even non-strings")
    void ignoresSiteAndServiceAccountFields() {
        storedWorkspace();
        ObjectNode input = json.createObjectNode().put("companyName", "Renamed");
        input.putObject("jiraSiteUrl").put("ignored", "http://localhost");
        input.putObject("serviceAccountEmail").put("ignored", "x");
        input.put("serviceAccountToken", 42);
        service.update(input);
        var saved = ArgumentCaptor.forClass(WorkspaceInfo.class);
        verify(repository).save(saved.capture());
        assertThat(saved.getValue().getJiraSiteAddress()).isEqualTo(SITE);
        verifyNoInteractions(linker);
    }

    @Test
    @DisplayName("AC-007.3 rejects absent, null, blank and non-string company names")
    void requiresCompanyName() {
        ObjectNode input = json.createObjectNode();
        updateError(input, "companyName", "MSG01", Map.of("field_name", "Company name"));
        input.putNull("companyName");
        updateError(input, "companyName", "MSG01", Map.of("field_name", "Company name"));
        input.put("companyName", " \t\n ");
        updateError(input, "companyName", "MSG01", Map.of("field_name", "Company name"));
        input.put("companyName", 42);
        updateError(input, "companyName", "MSG01", Map.of("field_name", "Company name"));
    }

    @Test
    @DisplayName("AC-007.3 rejects a company name over 100 characters and accepts exactly 100")
    void limitsCompanyName() {
        updateError(
                json.createObjectNode().put("companyName", "x".repeat(101)),
                "companyName",
                "MSG02",
                Map.of("field_name", "Company name", "max_length", 100));
        storedWorkspace();
        service.update(json.createObjectNode().put("companyName", "x".repeat(100)));
        verify(repository).save(any(WorkspaceInfo.class));
    }

    @ParameterizedTest
    @ValueSource(strings = {"null", "[]", "42", "{} {}", "{", ""})
    @DisplayName("AC-007.3 malformed update bodies return safe MSG03 without input or cause")
    void rejectsMalformedUpdateBody(String body) {
        assertThatThrownBy(() -> WorkspaceUpdateRequest.parse(body, json))
                .isInstanceOfSatisfying(FieldMessageCodeException.class, e -> {
                    assertThat(e.getCode()).isEqualTo("MSG03");
                    assertThat(e.getCause()).isNull();
                });
    }

    @Test
    @DisplayName("AC-007.9 update before setup returns MSG60 without external calls or changes")
    void rejectsMissingUpdateWorkspace() {
        assertThatThrownBy(() -> service.update(json.createObjectNode().put("companyName", "Renamed")))
                .isInstanceOfSatisfying(
                        NotFoundException.class, e -> assertThat(e.getCode()).isEqualTo("MSG60"));
        verify(repository, never()).save(any());
        verifyNoInteractions(linker, entities, audit, transactions);
    }
}
