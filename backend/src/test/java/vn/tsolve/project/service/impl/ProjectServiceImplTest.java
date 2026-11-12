package vn.tsolve.project.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import jakarta.validation.Validation;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import vn.tsolve.common.exception.BusinessRuleException;
import vn.tsolve.common.exception.CodedException;
import vn.tsolve.common.security.Role;
import vn.tsolve.common.security.TSolveUser;
import vn.tsolve.identity.signin.port.TenantScope;
import vn.tsolve.project.dto.CreateProjectRequest;
import vn.tsolve.project.dto.ProjectConnectionTestRequest;
import vn.tsolve.project.dto.ProjectConnectionTestResponse;
import vn.tsolve.project.entity.Project;
import vn.tsolve.project.entity.ProjectStatus;
import vn.tsolve.project.exception.ProjectAccessDeniedException;
import vn.tsolve.project.exception.ProjectTrackerUnavailableException;
import vn.tsolve.project.repository.ProjectLinkRepository;
import vn.tsolve.project.repository.ProjectLinkRepository.CallerDepartment;
import vn.tsolve.project.repository.ProjectLinkRepository.Link;
import vn.tsolve.project.repository.ProjectRepository;
import vn.tsolve.tracker.dto.TrackerProjectLookup;
import vn.tsolve.tracker.dto.TrackerProjectLookup.Outcome;
import vn.tsolve.tracker.dto.TrackerStatus;
import vn.tsolve.tracker.service.TrackerProjectService;

@ExtendWith(MockitoExtension.class)
class ProjectServiceImplTest {
    private static final UUID PROJECT = new UUID(127, 10);
    private static final UUID CALLER = new UUID(127, 11);
    private static final UUID OTHER = new UUID(127, 12);
    private static final CallerDepartment DEPARTMENT = new CallerDepartment(1L, new UUID(127, 1), "Operations");
    private static final List<TrackerStatus> STATUSES = List.of(
            new TrackerStatus("10000", "To Do", false),
            new TrackerStatus("10001", "In Review", false),
            new TrackerStatus("10002", "Done", true));

    @Mock
    private ProjectRepository projectRepository;

    @Mock
    private ProjectLinkRepository links;

    @Mock
    private TrackerProjectService tracker;

    @Mock
    private TenantScope scope;

    @BeforeEach
    void signIn() {
        TSolveUser user = new TSolveUser(CALLER, "pm@example.test", "Project Manager", Set.of(Role.PROJECT_MANAGER));
        SecurityContextHolder.getContext()
                .setAuthentication(UsernamePasswordAuthenticationToken.authenticated(user, null, user.authorities()));
        // Defaults also serve tests that deliberately stop before these collaborators.
        lenient().when(scope.currentWorkspaceId()).thenReturn(new UUID(127, 100));
        lenient().when(links.findCallerDepartment(CALLER)).thenReturn(Optional.of(DEPARTMENT));
        lenient().when(links.findLink(anyString())).thenReturn(Optional.empty());
        lenient()
                .when(tracker.findProject(anyString()))
                .thenAnswer(call -> TrackerProjectLookup.found(call.getArgument(0), STATUSES));
    }

    @AfterEach
    void clearCaller() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void findsTheActiveProjectOfAJiraKey_AC_002_14() {
        Project project = mock(Project.class);
        given(project.getPublicId()).willReturn(PROJECT);
        given(projectRepository.findByJiraProjectKeyAndStatus("ITSUP", ProjectStatus.ACTIVE))
                .willReturn(Optional.of(project));
        assertThat(service().findActivePublicIdByJiraProjectKey("ITSUP")).contains(PROJECT);
    }

    @Test
    void findsNothingWhenNoActiveProjectHasTheKey() {
        given(projectRepository.findByJiraProjectKeyAndStatus("OTHER", ProjectStatus.ACTIVE))
                .willReturn(Optional.empty());
        assertThat(service().findActivePublicIdByJiraProjectKey("OTHER")).isEmpty();
    }

    @Test
    void stripsAndUpperCasesTheKey_AC_001_1() {
        succeeds(" ops ");
        verify(links).findLink("OPS");
        verify(tracker).findProject("OPS");
    }

    @Test
    void upperCasesWithRootEvenUnderTurkishLocale_AC_001_1() {
        Locale previous = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr"));
            succeeds("qi");
            verify(links).findLink("QI");
            verify(tracker).findProject("QI");
        } finally {
            Locale.setDefault(previous);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"1OPS", "OP-S", "Ö", "A B", "x'; drop"})
    void refusesInvalidFormatBeforeLinkOrJira_AC_001_4(String key) {
        refusesUnavailableKey(key);
        verify(links, never()).findLink(anyString());
        verifyNoInteractions(tracker);
    }

    @ParameterizedTest
    @ValueSource(longs = {1, 2})
    void refusesActiveLinksInAnyDepartment_AC_001_5(long departmentId) {
        given(links.findLink("OPS")).willReturn(Optional.of(link(departmentId, "ACTIVE", OTHER, true, null, null)));
        refusesConflict("OPS", "Link Department");
        verifyNoInteractions(tracker);
    }

    @Test
    void refusesRemovedLinkInAnotherDepartment_AC_001_6() {
        given(links.findLink("OPS")).willReturn(Optional.of(link(2, "REMOVED", CALLER, false, null, null)));
        refusesConflict("OPS", "Link Department");
        verifyNoInteractions(tracker);
    }

    @Test
    void protectsAnotherEligibleManager_AC_001_7() {
        given(links.findLink("OPS")).willReturn(Optional.of(link(1, "REMOVED", OTHER, true, null, null)));
        refusesConflict("OPS", "Link Department");
        verifyNoInteractions(tracker);
    }

    @ParameterizedTest
    @CsvSource({"caller,true", "caller,false", "other,false", "none,false"})
    void allowsEligibleRemovedManagers_AC_001_7_AC_001_11(String manager, boolean protectedManager) {
        UUID managerId =
                switch (manager) {
                    case "caller" -> CALLER;
                    case "other" -> OTHER;
                    default -> null;
                };
        given(links.findLink("OPS"))
                .willReturn(Optional.of(link(1, "REMOVED", managerId, protectedManager, null, null)));
        assertThat(succeeds("OPS").reconnect()).isTrue();
        verify(tracker).findProject("OPS");
    }

    @Test
    void callsTrackerExactlyOnceAfterTheLocalLinkCheck_AC_001_8() {
        succeeds(" ops ");
        InOrder order = inOrder(links, tracker);
        order.verify(links).findCallerDepartment(CALLER);
        order.verify(links).findLink("OPS");
        order.verify(tracker).findProject("OPS");
        verify(tracker, times(1)).findProject("OPS");
        order.verifyNoMoreInteractions();
    }

    @Test
    void returnsCanonicalKeyAndAllStatusesInOrder_AC_001_9() {
        given(tracker.findProject("ALIAS")).willReturn(TrackerProjectLookup.found("OPS", STATUSES));
        ProjectConnectionTestResponse response = succeeds("ALIAS");
        assertThat(response.jiraProjectKey()).isEqualTo("OPS");
        assertThat(response.statuses()).containsExactlyElementsOf(STATUSES);
    }

    @Test
    void prefillsNewProjectWithDoneCategoryAndThreeDays_AC_001_10_AC_001_12() {
        ProjectConnectionTestResponse response = succeeds("OPS");
        assertThat(response.reconnect()).isFalse();
        assertThat(response.projectId()).isNull();
        assertThat(response.doneStatusIds()).containsExactly("10002");
        assertThat(response.reviewRange()).isEqualTo(3);
    }

    @Test
    void omitsNewProjectIdFromPlainJacksonJson_AC_001_10() throws Exception {
        ObjectMapper mapper = JsonMapper.builder().findAndAddModules().build();
        var response = new ProjectConnectionTestResponse("OPS", STATUSES, false, null, List.of("10002"), 3);
        assertThat(mapper.readTree(mapper.writeValueAsString(response)).has("projectId"))
                .isFalse();
    }

    @Test
    void retainsStoredIdsOnceInFirstSeenOrderAndRange_AC_001_11_AC_001_13_AC_001_14() {
        given(links.findLink("OPS"))
                .willReturn(Optional.of(link(1, "REMOVED", CALLER, true, List.of("10001", "STALE", "10001"), 5)));
        ProjectConnectionTestResponse response = succeeds("OPS");
        assertThat(response.reconnect()).isTrue();
        assertThat(response.projectId()).isEqualTo(PROJECT);
        assertThat(response.doneStatusIds()).containsExactly("10001");
        assertThat(response.reviewRange()).isEqualTo(5);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = "STALE")
    void fallsBackToDoneIdsWhenNoStoredIdRemains_AC_001_13(String stored) {
        List<String> ids = stored == null ? null : stored.isEmpty() ? List.of() : List.of(stored);
        given(links.findLink("OPS")).willReturn(Optional.of(link(1, "REMOVED", CALLER, true, ids, 5)));
        assertThat(succeeds("OPS").doneStatusIds()).containsExactly("10002");
    }

    @ParameterizedTest
    @CsvSource(
            value = {"NULL,3", "0,0", "-1,-1"},
            nullValues = "NULL")
    void defaultsOnlyNullStoredRange_AC_001_14(Integer stored, int expected) {
        given(links.findLink("OPS")).willReturn(Optional.of(link(1, "REMOVED", CALLER, true, null, stored)));
        assertThat(succeeds("OPS").reviewRange()).isEqualTo(expected);
    }

    @Test
    void prefillsNoIdsWhenJiraStatusesAreEmpty_AC_001_13() {
        given(links.findLink("OPS")).willReturn(Optional.of(link(1, "REMOVED", CALLER, true, List.of("10001"), 5)));
        given(tracker.findProject("OPS")).willReturn(TrackerProjectLookup.found("OPS", List.of()));
        assertThat(succeeds("OPS").doneStatusIds()).isEmpty();
    }

    @ParameterizedTest
    @EnumSource(
            value = Outcome.class,
            names = {"NOT_FOUND", "DENIED"})
    void refusesMissingOrDeniedJiraProject_AC_001_15(Outcome outcome) {
        given(tracker.findProject("OPS")).willReturn(TrackerProjectLookup.failed(outcome));
        refusesUnavailableKey("OPS");
        verify(tracker).findProject("OPS");
    }

    @Test
    void mapsUnavailableTrackerTo503_AC_001_16() {
        given(tracker.findProject("OPS")).willReturn(TrackerProjectLookup.failed(Outcome.UNAVAILABLE));
        assertThatThrownBy(() -> service().testConnection(new ProjectConnectionTestRequest("OPS")))
                .isInstanceOfSatisfying(ProjectTrackerUnavailableException.class, error -> {
                    assertThat(error.getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
                    assertThat(error.getCode()).isEqualTo("MSG06");
                    assertThat(error.getParams()).isEmpty();
                    assertThat(error.getField()).isNull();
                });
    }

    @ParameterizedTest
    @CsvSource({"2,ACTIVE,false", "2,REMOVED,false", "1,REMOVED,true"})
    void refusesCanonicalLinkConflicts_AC_001_19(long departmentId, String status, boolean protectedManager) {
        given(tracker.findProject("OPS")).willReturn(TrackerProjectLookup.found("LEGACY", STATUSES));
        given(links.findLink("LEGACY"))
                .willReturn(Optional.of(link(departmentId, status, OTHER, protectedManager, null, null)));
        refusesConflict("OPS", "Link Department");
        verify(links).findLink("LEGACY");
    }

    @ParameterizedTest
    @ValueSource(strings = {"ACTIVE", "REMOVED"})
    void refusesDistinctRequestedAndCanonicalProjectsNamingCanonicalDepartment_AC_001_21(String status) {
        given(links.findLink("OPS")).willReturn(Optional.of(link(1, "REMOVED", CALLER, true, null, null)));
        given(tracker.findProject("OPS")).willReturn(TrackerProjectLookup.found("LEGACY", STATUSES));
        given(links.findLink("LEGACY"))
                .willReturn(Optional.of(
                        new Link(OTHER, 1, "Canonical Department", "LEGACY", status, List.of(), 3, CALLER, false)));
        refusesConflict("OPS", "Canonical Department");
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void reconnectsTheOnlyEligibleAliasLink_AC_001_22(boolean requestedOwnsLink) {
        String storedKey = requestedOwnsLink ? "OPS" : "LEGACY";
        given(links.findLink(storedKey))
                .willReturn(Optional.of(
                        new Link(PROJECT, 1, "Operations", storedKey, "REMOVED", List.of("10001"), 5, CALLER, true)));
        given(tracker.findProject("OPS")).willReturn(TrackerProjectLookup.found("LEGACY", STATUSES));
        ProjectConnectionTestResponse response = succeeds("OPS");
        assertThat(response.reconnect()).isTrue();
        assertThat(response.projectId()).isEqualTo(PROJECT);
        assertThat(response.jiraProjectKey()).isEqualTo("LEGACY");
        verify(links).findLink("OPS");
        verify(links).findLink("LEGACY");
    }

    @Test
    void refusesIneligibleCallerBeforeEvenInvalidKeyFormat_AC_007_4_AC_007_8() {
        given(links.findCallerDepartment(CALLER)).willReturn(Optional.empty());
        refusesAccess("1BAD");
        verify(links, never()).findLink(anyString());
        verifyNoInteractions(tracker);
    }

    @Test
    void refusesMissingPrincipalBeforeRepositoryOrTracker_AC_007_4_AC_007_8() {
        SecurityContextHolder.clearContext();
        refusesAccess("1BAD");
        verify(scope).currentWorkspaceId();
        verifyNoInteractions(links, tracker);
    }

    @Test
    void propagatesUnboundWorkspaceBeforeAnyLookup_AC_007_5() {
        IllegalStateException failure = new IllegalStateException("no Workspace is bound");
        given(scope.currentWorkspaceId()).willThrow(failure);
        assertThatThrownBy(() -> service().testConnection(new ProjectConnectionTestRequest("OPS")))
                .isSameAs(failure);
        verifyNoInteractions(links, tracker);
    }

    @Test
    void acceptsOnlyProjectFieldsInRequestRecords_AC_007_7() {
        assertThat(Arrays.stream(ProjectConnectionTestRequest.class.getRecordComponents())
                        .map(c -> c.getName()))
                .containsExactly("jiraProjectKey");
        assertThat(Arrays.stream(CreateProjectRequest.class.getRecordComponents())
                        .map(c -> c.getName()))
                .containsExactly("jiraProjectKey", "doneStatusIds", "reviewRange");
    }

    @Test
    void derivesCallerFromSessionAfterWorkspaceBinding_AC_007_7_AC_007_8() {
        succeeds("OPS");
        InOrder order = inOrder(scope, links);
        order.verify(scope).currentWorkspaceId();
        order.verify(links).findCallerDepartment(CALLER);
    }

    @ParameterizedTest
    @NullAndEmptySource
    void requiresNonemptyDoneSelection_AC_002_4(List<String> ids) {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var violations = factory.getValidator().validate(new CreateProjectRequest("OPS", ids, BigDecimal.ONE));
            assertThat(violations).anySatisfy(violation -> {
                assertThat(violation.getPropertyPath().toString()).isEqualTo("doneStatusIds");
                assertThat(violation
                                .getConstraintDescriptor()
                                .getAnnotation()
                                .annotationType()
                                .getSimpleName())
                        .isEqualTo("NotEmpty");
                assertThat(violation.getMessage()).isEqualTo("MSG01");
            });
        }
    }

    @Test
    void requiresReviewRange_AC_002_6() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var violations = factory.getValidator().validate(new CreateProjectRequest("OPS", List.of("10002"), null));
            assertThat(violations).anySatisfy(violation -> {
                assertThat(violation.getPropertyPath().toString()).isEqualTo("reviewRange");
                assertThat(violation
                                .getConstraintDescriptor()
                                .getAnnotation()
                                .annotationType()
                                .getSimpleName())
                        .isEqualTo("NotNull");
                assertThat(violation.getMessage()).isEqualTo("MSG01");
            });
        }
    }

    @Test
    void acceptsValidCreateRequest_AC_002_4_AC_002_6() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            assertThat(factory.getValidator()
                            .validate(new CreateProjectRequest("OPS", List.of("10002"), BigDecimal.ONE)))
                    .isEmpty();
        }
    }

    @Test
    void excludesCredentialsAndRawJiraBodiesOnSuccessAndEveryFailurePath_AC_001_17() throws Exception {
        Logger logger = (Logger) LoggerFactory.getLogger("vn.tsolve.project");
        Level previous = logger.getLevel();
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.setLevel(Level.ALL);
        logger.addAppender(appender);
        ObjectMapper mapper = JsonMapper.builder().findAndAddModules().build();
        List<String> serialized = new ArrayList<>();
        try {
            serialized.add(mapper.writeValueAsString(succeeds("OPS")));
            for (String path : List.of(
                    "format",
                    "active",
                    "foreign",
                    "protected",
                    "canonical",
                    "ambiguous",
                    "NOT_FOUND",
                    "DENIED",
                    "UNAVAILABLE",
                    "access",
                    "principal",
                    "workspace")) {
                reset(links, tracker, scope);
                signIn();
                String key = "OPS";
                switch (path) {
                    case "format" -> key = "1BAD";
                    case "active" ->
                        given(links.findLink("OPS"))
                                .willReturn(Optional.of(link(1, "ACTIVE", CALLER, true, null, null)));
                    case "foreign" ->
                        given(links.findLink("OPS"))
                                .willReturn(Optional.of(link(2, "REMOVED", CALLER, false, null, null)));
                    case "protected" ->
                        given(links.findLink("OPS"))
                                .willReturn(Optional.of(link(1, "REMOVED", OTHER, true, null, null)));
                    case "canonical", "ambiguous" -> {
                        given(tracker.findProject("OPS")).willReturn(TrackerProjectLookup.found("LEGACY", STATUSES));
                        given(links.findLink("LEGACY"))
                                .willReturn(Optional.of(new Link(
                                        OTHER,
                                        1,
                                        "Canonical",
                                        "LEGACY",
                                        path.equals("canonical") ? "ACTIVE" : "REMOVED",
                                        null,
                                        null,
                                        CALLER,
                                        true)));
                        if (path.equals("ambiguous")) {
                            given(links.findLink("OPS"))
                                    .willReturn(Optional.of(link(1, "REMOVED", CALLER, true, null, null)));
                        }
                    }
                    case "access" -> given(links.findCallerDepartment(CALLER)).willReturn(Optional.empty());
                    case "principal" -> SecurityContextHolder.clearContext();
                    case "workspace" -> given(scope.currentWorkspaceId()).willThrow(new IllegalStateException());
                    default ->
                        given(tracker.findProject("OPS"))
                                .willReturn(TrackerProjectLookup.failed(Outcome.valueOf(path)));
                }
                String testedKey = key;
                assertThatThrownBy(() -> service().testConnection(new ProjectConnectionTestRequest(testedKey)))
                        .satisfies(error -> {
                            if (path.equals("workspace")) {
                                assertThat(error).isInstanceOf(IllegalStateException.class);
                                serialized.add("MSG06:{}:null");
                            } else {
                                assertThat(error).isInstanceOf(CodedException.class);
                                CodedException coded = (CodedException) error;
                                try {
                                    serialized.add(mapper.writeValueAsString(
                                            Arrays.asList(coded.getCode(), coded.getParams(), coded.getField())));
                                } catch (Exception e) {
                                    throw new AssertionError(e);
                                }
                            }
                        });
            }
            appender.list.forEach(event -> serialized.add(event.getFormattedMessage()));
            for (String value : serialized) {
                assertThat(value)
                        .doesNotContain("svc@acme.atlassian.test", "ATATT-planted-token", "{\"errorMessages\"");
            }
        } finally {
            logger.detachAppender(appender);
            logger.setLevel(previous);
            appender.stop();
        }
    }

    private ProjectConnectionTestResponse succeeds(String key) {
        AtomicReference<ProjectConnectionTestResponse> result = new AtomicReference<>();
        assertThatCode(() -> result.set(service().testConnection(new ProjectConnectionTestRequest(key))))
                .doesNotThrowAnyException();
        assertThat(result.get()).isNotNull();
        return result.get();
    }

    private void refusesUnavailableKey(String key) {
        assertThatThrownBy(() -> service().testConnection(new ProjectConnectionTestRequest(key)))
                .isInstanceOfSatisfying(BusinessRuleException.class, error -> {
                    assertThat(error.getCode()).isEqualTo("MSG48");
                    assertThat(error.getParams()).containsExactlyInAnyOrderEntriesOf(Map.of("tracker_name", "Jira"));
                    assertThat(error.getField()).isNull();
                });
    }

    private void refusesConflict(String key, String department) {
        assertThatThrownBy(() -> service().testConnection(new ProjectConnectionTestRequest(key)))
                .isInstanceOfSatisfying(BusinessRuleException.class, error -> {
                    assertThat(error.getCode()).isEqualTo("MSG49");
                    assertThat(error.getField()).isEqualTo("jiraProjectKey");
                    assertThat(error.getParams())
                            .containsExactlyInAnyOrderEntriesOf(
                                    Map.of("tracker_name", "Jira", "department_name", department));
                });
    }

    private void refusesAccess(String key) {
        assertThatThrownBy(() -> service().testConnection(new ProjectConnectionTestRequest(key)))
                .isInstanceOfSatisfying(ProjectAccessDeniedException.class, error -> {
                    assertThat(error.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
                    assertThat(error.getCode()).isEqualTo("MSG08");
                    assertThat(error.getParams()).isEmpty();
                    assertThat(error.getField()).isNull();
                });
    }

    private Link link(
            long department, String status, UUID manager, boolean protectedManager, List<String> ids, Integer range) {
        return new Link(PROJECT, department, "Link Department", "OPS", status, ids, range, manager, protectedManager);
    }

    private ProjectServiceImpl service() {
        return new ProjectServiceImpl(projectRepository, links, tracker, scope);
    }
}
