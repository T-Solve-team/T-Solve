package vn.tsolve.workspace.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.persistence.EntityManager;
import java.sql.SQLException;
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
import vn.tsolve.common.audit.AuditAction;
import vn.tsolve.common.audit.AuditService;
import vn.tsolve.common.exception.BusinessRuleException;
import vn.tsolve.common.exception.FieldMessageCodeException;
import vn.tsolve.common.exception.NotFoundException;
import vn.tsolve.identity.signin.port.TenantScope;
import vn.tsolve.tracker.client.TicketTrackerClient;
import vn.tsolve.tracker.dto.JiraAccount;
import vn.tsolve.tracker.exception.JiraRejectedException;
import vn.tsolve.tracker.exception.JiraUnavailableException;
import vn.tsolve.workspace.crypto.TokenCipher;
import vn.tsolve.workspace.dto.WorkspaceInfoMapper;
import vn.tsolve.workspace.dto.WorkspaceInfoResponse;
import vn.tsolve.workspace.dto.WorkspaceSetupRequest;
import vn.tsolve.workspace.entity.WorkspaceInfo;
import vn.tsolve.workspace.repository.WorkspaceInfoRepository;
import vn.tsolve.workspace.site.JiraSiteLinkRefusedException;
import vn.tsolve.workspace.site.JiraSiteLinkRefusedException.Reason;
import vn.tsolve.workspace.site.JiraSiteLinker;

class WorkspaceServiceImplTest {
    private static final String SITE = "https://fake.atlassian.net";
    private static final String TOKEN = "obviously-fake-jira-token";
    private final ObjectMapper json = new ObjectMapper();
    private final UUID workspaceId = UUID.randomUUID();
    private final TenantScope scope = mock(TenantScope.class);
    private final WorkspaceInfoRepository repository = mock(WorkspaceInfoRepository.class);
    private final EntityManager entities = mock(EntityManager.class);
    private final TicketTrackerClient tracker = mock(TicketTrackerClient.class);
    private final JiraSiteLinker linker = mock(JiraSiteLinker.class);
    private final TokenCipher cipher = mock(TokenCipher.class);
    private final AuditService audit = mock(AuditService.class);
    private WorkspaceServiceImpl service;

    @BeforeEach
    void setup() {
        when(scope.currentWorkspaceId()).thenReturn(workspaceId);
        when(cipher.encrypt(workspaceId, TOKEN)).thenReturn("fake-ciphertext");
        service = new WorkspaceServiceImpl(
                scope,
                repository,
                entities,
                tracker,
                linker,
                cipher,
                audit,
                Mappers.getMapper(WorkspaceInfoMapper.class));
    }

    private ObjectNode valid() {
        return json.createObjectNode()
                .put("companyName", "Fake company")
                .put("jiraSiteUrl", " HTTPS://FAKE.ATLASSIAN.NET/ ")
                .put("serviceAccountEmail", "fake@example.test")
                .put("serviceAccountToken", TOKEN);
    }

    private void error(JsonNode input, String field, String code, Map<String, Object> params) {
        assertThatThrownBy(() -> service.setup(input)).isInstanceOfSatisfying(FieldMessageCodeException.class, e -> {
            assertThat(e.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(e.getField()).isEqualTo(field);
            assertThat(e.getCode()).isEqualTo(code);
            assertThat(e.getParams()).isEqualTo(params);
            assertThat(e.getCause()).isNull();
            assertThat(e.toString()).doesNotContain(TOKEN);
        });
        verifyNoInteractions(entities, audit, cipher);
    }

    @Test
    @DisplayName("AC-001.1 inserts bound id, GOOGLE, normalized site and encrypted token")
    void insertsSetup() {
        WorkspaceInfoResponse response = service.setup(valid());
        var saved = ArgumentCaptor.forClass(WorkspaceInfo.class);
        verify(entities).persist(saved.capture());
        verify(entities).flush();
        WorkspaceInfo row = saved.getValue();
        assertThat(row.getId()).isEqualTo(workspaceId);
        assertThat(row.getCompanyName()).isEqualTo("Fake company");
        assertThat(row.getJiraSiteAddress()).isEqualTo(SITE);
        assertThat(row.getServiceAccountEmail()).isEqualTo("fake@example.test");
        assertThat(row.getSignInProvider()).isEqualTo("GOOGLE");
        assertThat(row.getServiceAccountApiToken()).isEqualTo("fake-ciphertext");
        assertThat(row.getTokenExpiryDate()).isNull();
        assertThat(response).isEqualTo(new WorkspaceInfoResponse("Fake company", SITE, "fake@example.test"));
    }

    @ParameterizedTest
    @CsvSource({
        "companyName,Company name",
        "jiraSiteUrl,Jira site address",
        "serviceAccountEmail,Service account email",
        "serviceAccountToken,Service account API token"
    })
    @DisplayName("AC-001.2 rejects missing, null, blank and non-string required fields before any check")
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
        verifyNoInteractions(repository, tracker, linker);
    }

    @ParameterizedTest
    @CsvSource({
        "companyName,Company name,100",
        "jiraSiteUrl,Jira site address,200",
        "serviceAccountEmail,Service account email,100"
    })
    @DisplayName("AC-001.3 measures raw length and returns MSG02 with field_name and max_length")
    void limitsFields(String field, String label, int limit) {
        error(
                valid().put(field, "x".repeat(limit + 1)),
                field,
                "MSG02",
                Map.of("field_name", label, "max_length", limit));
        verifyNoInteractions(repository, tracker, linker);
    }

    @Test
    @DisplayName("AC-001.3 accepts company, site and email exactly at their limits")
    void acceptsLimits() {
        String email = "a".repeat(86) + "@example.test";
        String site = " ".repeat(200 - SITE.length()) + SITE;
        assertThat(email).hasSize(99);
        email = "a" + email;
        service.setup(valid().put("companyName", "x".repeat(100))
                .put("jiraSiteUrl", site)
                .put("serviceAccountEmail", email));
        verify(entities).persist(any(WorkspaceInfo.class));
    }

    @ParameterizedTest
    @ValueSource(strings = {"bad", "a b@example.test", "a@@example.test", "a@example", "a@", "@example.test"})
    @DisplayName("AC-001.4 rejects malformed email without contacting Jira")
    void rejectsEmail(String email) {
        error(valid().put("serviceAccountEmail", email), "serviceAccountEmail", "MSG03", Map.of());
        verifyNoInteractions(repository, tracker, linker);
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
    @DisplayName("AC-001.5 rejects unsafe sites before checking the row or making outbound calls")
    void rejectsSite(String site) {
        error(valid().put("jiraSiteUrl", site), "jiraSiteUrl", "MSG61", Map.of());
        verifyNoInteractions(repository, tracker, linker);
    }

    @Test
    @DisplayName("AC-001.6 checks the normalized Jira account before linking or inserting")
    void checksAccountInOrder() {
        service.setup(valid());
        var order = inOrder(repository, tracker, linker, cipher, entities, audit);
        order.verify(repository).count();
        order.verify(tracker).checkServiceAccount(new JiraAccount(SITE, "fake@example.test", TOKEN));
        order.verify(linker).linkJiraSite(SITE);
        order.verify(cipher).encrypt(workspaceId, TOKEN);
        order.verify(entities).persist(any(WorkspaceInfo.class));
        order.verify(entities).flush();
        order.verify(audit)
                .record(
                        AuditAction.TRACKER_CONFIGURATION_CHANGE,
                        "WORKSPACE",
                        workspaceId,
                        Map.of(
                                "companyName",
                                "Fake company",
                                "jiraSiteUrl",
                                SITE,
                                "serviceAccountEmail",
                                "fake@example.test"));
    }

    @ParameterizedTest
    @ValueSource(ints = {401, 403, 500, 0})
    @DisplayName("AC-001.7 maps Jira rejection, outage and timeout to MSG156 without linking or leaking credentials")
    void rejectsJiraFailure(int status) {
        RuntimeException failure = status == 401 || status == 403
                ? new JiraRejectedException(status)
                : new JiraUnavailableException(status);
        doThrow(failure).when(tracker).checkServiceAccount(any());
        error(valid(), "serviceAccountToken", "MSG156", Map.of("tracker_name", "Jira"));
        verifyNoInteractions(linker);
    }

    @Test
    @DisplayName("AC-001.8 setup links and inserts inside its transactional service boundary")
    void usesTransaction() throws Exception {
        assertThat(WorkspaceServiceImpl.class
                        .getMethod("setup", JsonNode.class)
                        .getAnnotation(org.springframework.transaction.annotation.Transactional.class))
                .isNotNull();
        service.setup(valid());
        var order = inOrder(linker, entities);
        order.verify(linker).linkJiraSite(SITE);
        order.verify(entities).persist(any());
    }

    @Test
    @DisplayName("AC-001.9 linker refusal for another Workspace returns MSG153 without inserting")
    void rejectsLinkedElsewhere() {
        when(linker.linkJiraSite(SITE)).thenThrow(new JiraSiteLinkRefusedException(Reason.LINKED_ELSEWHERE));
        error(valid(), "jiraSiteUrl", "MSG153", Map.of("tracker_name", "Jira"));
    }

    @Test
    @DisplayName("AC-001.10 unreadable tenant info returns MSG61 and logs no token")
    void rejectsUnreadable() {
        var logger = (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger(WorkspaceServiceImpl.class);
        var logs = new ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent>();
        logs.start();
        logger.addAppender(logs);
        try {
            when(linker.linkJiraSite(SITE)).thenThrow(new JiraSiteLinkRefusedException(Reason.UNREADABLE));
            error(valid(), "jiraSiteUrl", "MSG61", Map.of());
            assertThat(logs.list).isNotEmpty().allSatisfy(event -> {
                assertThat(event.getFormattedMessage()).doesNotContain(TOKEN);
                assertThat(event.getThrowableProxy()).isNull();
            });
        } finally {
            logger.detachAppender(logs);
            logs.stop();
        }
    }

    @Test
    @DisplayName("AC-001.11 an existing row returns WORKSPACE_ALREADY_SET_UP before external calls")
    void rejectsExisting() {
        when(repository.count()).thenReturn(1L);
        assertThatThrownBy(() -> service.setup(valid()))
                .isInstanceOfSatisfying(BusinessRuleException.class, e -> assertThat(e.getCode())
                        .isEqualTo("WORKSPACE_ALREADY_SET_UP"));
        verifyNoInteractions(tracker, linker, cipher, entities, audit);
    }

    @ParameterizedTest
    @ValueSource(strings = {"uq_workspace_singleton", "pk_workspace_info"})
    @DisplayName("AC-001.11 concurrent singleton or primary key insert violation returns a safe conflict")
    void rejectsConcurrentInsert(String constraint) {
        doThrow(new ConstraintViolationException(
                        "fake-ciphertext", new SQLException("fake database error", "23505"), constraint))
                .when(entities)
                .flush();
        assertThatThrownBy(() -> service.setup(valid())).isInstanceOfSatisfying(BusinessRuleException.class, e -> {
            assertThat(e.getCode()).isEqualTo("WORKSPACE_ALREADY_SET_UP");
            assertThat(e.getCause()).isNull();
            assertThat(e.toString()).doesNotContain(TOKEN, "fake-ciphertext");
        });
        verifyNoInteractions(audit);
    }

    @Test
    @DisplayName("AC-001.11 unrelated integrity failures are not mislabeled as already set up")
    void preservesOtherFailure() {
        var failure = new ConstraintViolationException(
                "fake database error", new SQLException("check", "23514"), "ck_workspace_singleton");
        doThrow(failure).when(entities).flush();
        assertThatThrownBy(() -> service.setup(valid())).isSameAs(failure);
        verifyNoInteractions(audit);
    }

    @Test
    @DisplayName("AC-001.12 audits exactly the three public setup fields without token or ciphertext")
    void auditsSafeDetails() throws Exception {
        WorkspaceInfoResponse response = service.setup(valid());
        verify(audit)
                .record(
                        AuditAction.TRACKER_CONFIGURATION_CHANGE,
                        "WORKSPACE",
                        workspaceId,
                        Map.of(
                                "companyName",
                                "Fake company",
                                "jiraSiteUrl",
                                SITE,
                                "serviceAccountEmail",
                                "fake@example.test"));
        assertThat(json.writeValueAsString(response)).doesNotContain(TOKEN, "fake-ciphertext", "Token", "token");
        assertThat(WorkspaceSetupRequest.parse(json.writeValueAsString(valid()), json)
                        .toString())
                .doesNotContain(TOKEN);
    }

    @Test
    @DisplayName("AC-002.2 reads only the three public fields of the bound Workspace")
    void readsWorkspace() throws Exception {
        when(repository.findById(workspaceId))
                .thenReturn(Optional.of(
                        new WorkspaceInfo(workspaceId, "Fake company", SITE, "fake@example.test", "fake-ciphertext")));
        JsonNode result = json.valueToTree(service.get());
        assertThat(result.size()).isEqualTo(3);
        assertThat(result)
                .isEqualTo(
                        json.readTree(
                                "{\"companyName\":\"Fake company\",\"jiraSiteUrl\":\"https://fake.atlassian.net\",\"serviceAccountEmail\":\"fake@example.test\"}"));
        verifyNoInteractions(tracker, linker, cipher, entities, audit);
    }

    @Test
    @DisplayName("AC-002.3 absent Workspace information returns MSG60")
    void rejectsMissingWorkspace() {
        assertThatThrownBy(service::get).isInstanceOfSatisfying(NotFoundException.class, e -> assertThat(e.getCode())
                .isEqualTo("MSG60"));
        verifyNoInteractions(tracker, linker, cipher, entities, audit);
    }

    @ParameterizedTest
    @ValueSource(strings = {"null", "[]", "42", "{} {}", "{", ""})
    @DisplayName("AC-001.2 malformed or non-object request bodies return safe MSG03")
    void rejectsMalformedBody(String input) {
        assertThatThrownBy(() -> WorkspaceSetupRequest.parse(input, json))
                .isInstanceOfSatisfying(FieldMessageCodeException.class, e -> {
                    assertThat(e.getCode()).isEqualTo("MSG03");
                    assertThat(e.getCause()).isNull();
                });
    }
}
