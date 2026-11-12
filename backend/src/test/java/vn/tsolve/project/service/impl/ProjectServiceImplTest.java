package vn.tsolve.project.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import jakarta.validation.Validation;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import vn.tsolve.common.audit.AuditAction;
import vn.tsolve.common.audit.AuditService;
import vn.tsolve.common.exception.BusinessRuleException;
import vn.tsolve.common.exception.FieldMessageCodeException;
import vn.tsolve.common.security.Role;
import vn.tsolve.common.security.TSolveUser;
import vn.tsolve.identity.signin.port.TenantScope;
import vn.tsolve.project.dto.CreateProjectRequest;
import vn.tsolve.project.dto.ProjectConnectionTestRequest;
import vn.tsolve.project.dto.ProjectConnectionTestResponse;
import vn.tsolve.project.dto.ProjectResponse;
import vn.tsolve.project.dto.ProjectSaveResult;
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
    private static final UUID NEW_ID = new UUID(127, 30);
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

    @Mock
    private AuditService audit;

    @Mock
    private PlatformTransactionManager transactionManager;

    @Mock
    private TransactionStatus transactionStatus;

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
        lenient().when(transactionManager.getTransaction(any())).thenReturn(transactionStatus);
        lenient()
                .when(links.insertProject(anyLong(), anyString(), anyList(), anyInt()))
                .thenReturn(Optional.of(NEW_ID));
        lenient().when(links.readProject(NEW_ID)).thenReturn(Optional.of(saved("OPS", List.of("10002"), 3)));
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
        return new ProjectServiceImpl(projectRepository, links, tracker, scope, audit, transactionManager);
    }

    @ParameterizedTest
    @ValueSource(strings = {"1OPS", "OP-S", "A B", "x'; drop"})
    void refusesCreateFormatBeforeLinksOrJira_AC_002_1_AC_002_21(String key) {
        assertCreateConflict(create(key, List.of("10002"), "3"), "MSG48", null);
        verify(links, never()).findLink(anyString());
        assertBeforeJiraRefusal();
    }

    @ParameterizedTest
    @CsvSource({"1,ACTIVE,false", "2,ACTIVE,false", "2,REMOVED,false", "1,REMOVED,true"})
    void refusesCreateLinkedProjectsBeforeJira_AC_002_1(long department, String status, boolean protectedManager) {
        given(links.findLink("OPS"))
                .willReturn(Optional.of(link(department, status, OTHER, protectedManager, null, null)));
        assertCreateConflict(create("OPS", List.of("10002"), "3"), "MSG49", "Link Department");
        assertBeforeJiraRefusal();
    }

    @ParameterizedTest
    @CsvSource({"2,ACTIVE,false", "2,REMOVED,false", "1,REMOVED,true"})
    void refusesCreateCanonicalConflicts_AC_002_1(long department, String status, boolean protectedManager) {
        given(tracker.findProject("OPSALIAS")).willReturn(TrackerProjectLookup.found("OPS", STATUSES));
        given(links.findLink("OPS"))
                .willReturn(Optional.of(link(department, status, OTHER, protectedManager, null, null)));
        assertCreateConflict(create("OPSALIAS", List.of("10002"), "3"), "MSG49", "Link Department");
        verify(tracker).findProject("OPSALIAS");
        assertBeforeTransactionRefusal();
    }

    @ParameterizedTest
    @ValueSource(strings = {"ACTIVE", "REMOVED"})
    void refusesCreateDistinctProjectsNamingCanonicalDepartment_AC_002_1(String status) {
        given(links.findLink("OPSALIAS")).willReturn(Optional.of(link(1, "REMOVED", CALLER, true, null, null)));
        given(tracker.findProject("OPSALIAS")).willReturn(TrackerProjectLookup.found("OPS", STATUSES));
        given(links.findLink("OPS"))
                .willReturn(Optional.of(
                        new Link(OTHER, 1, "Canonical Department", "OPS", status, List.of(), 3, CALLER, false)));
        assertCreateConflict(create("OPSALIAS", List.of("10002"), "3"), "MSG49", "Canonical Department");
        verify(tracker).findProject("OPSALIAS");
        assertBeforeTransactionRefusal();
    }

    @Test
    void normalizesCreateKeyWithRootLocale_AC_002_1() {
        Locale previous = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr"));
            save(create(" ops ", List.of("10002"), "3"));
            verify(links, times(2)).findLink("OPS");
            verify(tracker).findProject("OPS");
            save(create("qi", List.of("10002"), "3"));
            verify(links, times(2)).findLink("QI");
            verify(tracker).findProject("QI");
        } finally {
            Locale.setDefault(previous);
        }
    }

    @Test
    void readsJiraOnceBeforeTheSingleWriteTransaction_AC_002_2_AC_002_18() {
        save(create("OPS", List.of("10002"), "3"));
        InOrder order = inOrder(links, tracker, transactionManager);
        order.verify(links).findLink("OPS");
        order.verify(tracker).findProject("OPS");
        order.verify(transactionManager).getTransaction(any());
        order.verify(links).insertProject(DEPARTMENT.departmentId(), "OPS", List.of("10002"), 3);
        order.verify(transactionManager).commit(transactionStatus);
        verify(tracker, times(1)).findProject(anyString());
        verify(transactionManager, times(1)).getTransaction(any());
        verify(transactionManager, never()).rollback(any());
    }

    @ParameterizedTest
    @EnumSource(
            value = Outcome.class,
            names = {"NOT_FOUND", "DENIED"})
    void refusesCreateMissingOrDeniedJira_AC_002_3(Outcome outcome) {
        given(tracker.findProject("OPS")).willReturn(TrackerProjectLookup.failed(outcome));
        assertCreateConflict(create("OPS", List.of("10002"), "3"), "MSG48", null);
        verify(tracker).findProject("OPS");
        assertBeforeTransactionRefusal();
    }

    @Test
    void refusesCreateUnavailableJira_AC_002_3() {
        given(tracker.findProject("OPS")).willReturn(TrackerProjectLookup.failed(Outcome.UNAVAILABLE));
        assertThatThrownBy(() -> service().create(create("OPS", List.of("10002"), "3")))
                .isInstanceOfSatisfying(ProjectTrackerUnavailableException.class, error -> {
                    assertThat(error.getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
                    assertThat(error.getCode()).isEqualTo("MSG06");
                    assertThat(error.getParams()).isEmpty();
                    assertThat(error.getField()).isNull();
                });
        verify(tracker).findProject("OPS");
        assertBeforeTransactionRefusal();
    }

    @ParameterizedTest
    @NullAndEmptySource
    void refusesDirectCreateWithoutIds_AC_002_4(List<String> ids) {
        assertCreateField(create("OPS", ids, "3"), "MSG01", "doneStatusIds");
        assertBeforeJiraRefusal();
    }

    static Stream<List<String>> invalidCreateIds() {
        return Stream.of(Arrays.asList((String) null), List.of(""), List.of("  "), Arrays.asList("10002", null));
    }

    @ParameterizedTest
    @MethodSource("invalidCreateIds")
    void refusesDirectCreateBlankIds_AC_002_5_AC_002_21(List<String> ids) {
        assertCreateField(create("OPS", ids, "3"), "MSG01", "doneStatusIds");
        verify(links, never()).findLink(anyString());
        assertBeforeJiraRefusal();
    }

    @Test
    void refusesUnknownCreateIdAfterJira_AC_002_5_AC_002_21() {
        assertCreateField(create("OPS", List.of("99999"), "3"), "MSG01", "doneStatusIds");
        verify(tracker).findProject("OPS");
        assertBeforeTransactionRefusal();
    }

    @Test
    void refusesDirectCreateWithoutRange_AC_002_6() {
        assertCreateField(create("OPS", List.of("10002"), null), "MSG01", "reviewRange");
        assertBeforeJiraRefusal();
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "2.5", "1.5", "2147483648", "1E+10"})
    void refusesCreateRangeWithoutTruncation_AC_002_7_AC_002_21(String range) {
        assertCreateField(create("OPS", List.of("10002"), range), "MSG44", "reviewRange");
        verify(links, never()).findLink(anyString());
        assertBeforeJiraRefusal();
    }

    @ParameterizedTest
    @CsvSource({"2147483647,2147483647", "1,1", "1.0,1"})
    void storesExactWholeCreateRange_AC_002_7(String range, int expected) {
        save(create("OPS", List.of("10002"), range));
        verify(links).insertProject(DEPARTMENT.departmentId(), "OPS", List.of("10002"), expected);
    }

    @Test
    void checksCreateEligibilityBeforeRange_AC_002_21_AC_007_4_AC_007_8() {
        given(links.findCallerDepartment(CALLER)).willReturn(Optional.empty());
        assertCreateAccess(create("1BAD", List.of("10002"), "0"));
        verify(links, never()).findLink(anyString());
        assertBeforeJiraRefusal();
    }

    @Test
    void checksCreateFormatBeforeRange_AC_002_21() {
        assertCreateConflict(create("1BAD", List.of("10002"), "0"), "MSG48", null);
        verify(links, never()).findLink(anyString());
        assertBeforeJiraRefusal();
    }

    @Test
    void checksCreateRangeBeforeActiveLink_AC_002_21() {
        lenient().when(links.findLink("OPS")).thenReturn(Optional.of(link(2, "ACTIVE", OTHER, true, null, null)));
        assertCreateField(create("OPS", List.of("10002"), "0"), "MSG44", "reviewRange");
        verify(links, never()).findLink(anyString());
        assertBeforeJiraRefusal();
    }

    @Test
    void checksBlankCreateIdBeforeActiveLink_AC_002_21() {
        lenient().when(links.findLink("OPS")).thenReturn(Optional.of(link(2, "ACTIVE", OTHER, true, null, null)));
        assertCreateField(create("OPS", List.of(" "), "3"), "MSG01", "doneStatusIds");
        verify(links, never()).findLink(anyString());
        assertBeforeJiraRefusal();
    }

    @Test
    void checksActiveLinkBeforeUnknownCreateId_AC_002_21() {
        given(links.findLink("OPS")).willReturn(Optional.of(link(2, "ACTIVE", OTHER, true, null, null)));
        assertCreateConflict(create("OPS", List.of("99999"), "3"), "MSG49", "Link Department");
        assertBeforeJiraRefusal();
    }

    @Test
    void insertsCanonicalCreateKey_AC_002_8() {
        given(tracker.findProject("OPSALIAS")).willReturn(TrackerProjectLookup.found("OPS", STATUSES));
        save(create("OPSALIAS", List.of("10002"), "3"));
        verify(links).insertProject(DEPARTMENT.departmentId(), "OPS", List.of("10002"), 3);
        InOrder order = inOrder(links, transactionManager);
        order.verify(transactionManager).getTransaction(any());
        order.verify(links).findCallerDepartment(CALLER);
        order.verify(links).findLink("OPSALIAS");
        order.verify(links).findLink("OPS");
        order.verify(links).insertProject(DEPARTMENT.departmentId(), "OPS", List.of("10002"), 3);
    }

    @Test
    void maintainsManagerThenAuditsTheSavedProjection_AC_002_9_AC_002_10_AC_002_13() {
        ProjectResponse projection = saved("STORED", List.of("10001"), 7);
        given(links.readProject(NEW_ID)).willReturn(Optional.of(projection));
        assertThat(save(create("OPS", List.of("10002"), "3"))).isEqualTo(new ProjectSaveResult(projection, true));
        Map<String, Object> details = details(projection);
        InOrder order = inOrder(links, audit);
        order.verify(links).insertProject(DEPARTMENT.departmentId(), "OPS", List.of("10002"), 3);
        order.verify(links).upsertManager(CALLER, NEW_ID);
        order.verify(links).readProject(NEW_ID);
        order.verify(audit).record(AuditAction.TRACKER_CONFIGURATION_CHANGE, "PROJECT", NEW_ID, details);
        verify(links, times(1)).upsertManager(CALLER, NEW_ID);
        verify(audit, times(1)).record(any(), anyString(), any(), any());
        order.verifyNoMoreInteractions();
    }

    @Test
    void refusesLostInsertWithoutReconnectingAndRollsBack_AC_002_11() {
        given(links.insertProject(anyLong(), anyString(), anyList(), anyInt())).willReturn(Optional.empty());
        given(links.findLink("OPS"))
                .willReturn(
                        Optional.empty(), Optional.empty(), Optional.of(link(2, "ACTIVE", OTHER, true, null, null)));
        assertCreateConflict(create("OPS", List.of("10002"), "3"), "MSG49", "Link Department");
        verify(links).insertProject(DEPARTMENT.departmentId(), "OPS", List.of("10002"), 3);
        verify(links, never()).upsertManager(any(), any());
        verify(links, never()).lockProject(any());
        verify(links, never()).readProject(any());
        verifyNoInteractions(audit);
        verify(transactionManager).rollback(transactionStatus);
        verify(transactionManager, never()).commit(any());
    }

    @Test
    void propagatesMembershipFailureAndRollsBack_AC_002_12() {
        var failure = new DataIntegrityViolationException("membership");
        doThrow(failure).when(links).upsertManager(CALLER, NEW_ID);
        assertThatThrownBy(() -> service().create(create("OPS", List.of("10002"), "3")))
                .isSameAs(failure);
        verify(links).insertProject(DEPARTMENT.departmentId(), "OPS", List.of("10002"), 3);
        verifyNoInteractions(audit);
        verify(transactionManager).rollback(transactionStatus);
        verify(transactionManager, never()).commit(any());
    }

    @Test
    void propagatesAuditFailureAndRollsBack_AC_002_12() {
        var failure = new IllegalStateException("audit");
        doThrow(failure).when(audit).record(any(), anyString(), any(), any());
        assertThatThrownBy(() -> service().create(create("OPS", List.of("10002"), "3")))
                .isSameAs(failure);
        verify(links).upsertManager(CALLER, NEW_ID);
        verify(transactionManager).rollback(transactionStatus);
        verify(transactionManager, never()).commit(any());
    }

    @Test
    void deduplicatesCreateIdsInFirstSeenOrder_AC_002_15() {
        List<String> unique = List.of("10002", "10001");
        ProjectResponse projection = saved("OPS", unique, 3);
        given(links.readProject(NEW_ID)).willReturn(Optional.of(projection));
        assertThat(save(create("OPS", List.of("10002", "10001", "10002", "10001"), "3"))
                        .project())
                .isEqualTo(projection);
        verify(links).insertProject(DEPARTMENT.departmentId(), "OPS", unique, 3);
        verify(audit).record(AuditAction.TRACKER_CONFIGURATION_CHANGE, "PROJECT", NEW_ID, details(projection));
    }

    @Test
    void refusesLostCreateEligibilityInsideTransaction_AC_002_17() {
        given(links.findCallerDepartment(CALLER)).willReturn(Optional.of(DEPARTMENT), Optional.empty());
        assertCreateAccess(create("OPS", List.of("10002"), "3"));
        InOrder order = inOrder(links, transactionManager);
        order.verify(links).findCallerDepartment(CALLER);
        order.verify(transactionManager).getTransaction(any());
        order.verify(links).findCallerDepartment(CALLER);
        assertNoCreateWrites();
        verify(transactionManager).rollback(transactionStatus);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void locksAndRereadsRequestedOrCanonicalLinkInsideTransaction_AC_002_17(boolean alias) {
        String requested = alias ? "OPSALIAS" : "OPS";
        if (alias) {
            given(tracker.findProject(requested)).willReturn(TrackerProjectLookup.found("OPS", STATUSES));
        }
        Link appeared = link(2, "ACTIVE", OTHER, true, null, null);
        given(links.findLink("OPS")).willReturn(Optional.empty(), Optional.of(appeared), Optional.of(appeared));
        assertCreateConflict(create(requested, List.of("10002"), "3"), "MSG49", "Link Department");
        InOrder order = inOrder(links, transactionManager);
        order.verify(transactionManager).getTransaction(any());
        order.verify(links).findCallerDepartment(CALLER);
        if (alias) {
            order.verify(links).findLink(requested);
        }
        order.verify(links).findLink("OPS");
        order.verify(links).lockProject(PROJECT);
        order.verify(links).findLink("OPS");
        assertNoCreateWrites();
        verify(transactionManager).rollback(transactionStatus);
    }

    @Test
    void usesDepartmentReReadInsideTransaction_AC_002_17() {
        CallerDepartment moved = new CallerDepartment(2, OTHER, "New Department");
        given(links.findCallerDepartment(CALLER)).willReturn(Optional.of(DEPARTMENT), Optional.of(moved));
        save(create("OPS", List.of("10002"), "3"));
        verify(links).insertProject(2, "OPS", List.of("10002"), 3);
    }

    @Test
    void doesNotPretendToReconnectAnEligibleRemovedProject() {
        given(links.findLink("OPS")).willReturn(Optional.of(link(1, "REMOVED", CALLER, true, null, null)));
        assertThatThrownBy(() -> service().create(create("OPS", List.of("10002"), "3")))
                .isInstanceOf(UnsupportedOperationException.class);
        assertNoCreateWrites();
        verify(links, never()).upsertManager(any(), any());
    }

    @Test
    void refusesMissingCreatePrincipalBeforeInvalidSettings_AC_007_4_AC_007_8() {
        SecurityContextHolder.clearContext();
        assertCreateAccess(create("1BAD", List.of("10002"), "0"));
        verify(scope).currentWorkspaceId();
        verifyNoInteractions(links, tracker, audit, transactionManager);
    }

    @Test
    void propagatesUnboundCreateWorkspaceBeforeAnyLookup_AC_007_5() {
        var failure = new IllegalStateException("no Workspace is bound");
        given(scope.currentWorkspaceId()).willThrow(failure);
        assertThatThrownBy(() -> service().create(create("OPS", List.of("10002"), "3")))
                .isSameAs(failure);
        verifyNoInteractions(links, tracker, audit, transactionManager);
    }

    @Test
    void checksCreateWorkspaceBeforeCallerDepartment_AC_007_8() {
        save(create("OPS", List.of("10002"), "3"));
        InOrder order = inOrder(scope, links);
        order.verify(scope).currentWorkspaceId();
        order.verify(links).findCallerDepartment(CALLER);
    }

    private CreateProjectRequest create(String key, List<String> ids, String range) {
        return new CreateProjectRequest(key, ids, range == null ? null : new BigDecimal(range));
    }

    private ProjectSaveResult save(CreateProjectRequest request) {
        AtomicReference<ProjectSaveResult> result = new AtomicReference<>();
        assertThatCode(() -> result.set(service().create(request))).doesNotThrowAnyException();
        assertThat(result.get()).isNotNull();
        return result.get();
    }

    private ProjectResponse saved(String key, List<String> ids, int range) {
        return new ProjectResponse(NEW_ID, DEPARTMENT.departmentPublicId(), key, ids, range, "ACTIVE");
    }

    private Map<String, Object> details(ProjectResponse saved) {
        return Map.of(
                "operation",
                "CREATE",
                "jiraProjectKey",
                saved.jiraProjectKey(),
                "departmentId",
                saved.departmentId(),
                "doneStatusIds",
                saved.doneStatusIds(),
                "reviewRange",
                saved.reviewRange());
    }

    private void assertCreateField(CreateProjectRequest request, String code, String field) {
        assertThatThrownBy(() -> service().create(request))
                .isInstanceOfSatisfying(FieldMessageCodeException.class, error -> {
                    assertThat(error.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(error.getCode()).isEqualTo(code);
                    assertThat(error.getField()).isEqualTo(field);
                    assertThat(error.getParams())
                            .containsExactlyInAnyOrderEntriesOf(
                                    code.equals("MSG44") ? Map.of() : Map.of("field_name", field));
                });
    }

    private void assertCreateConflict(CreateProjectRequest request, String code, String department) {
        assertThatThrownBy(() -> service().create(request))
                .isInstanceOfSatisfying(BusinessRuleException.class, error -> {
                    assertThat(error.getCode()).isEqualTo(code);
                    assertThat(error.getField()).isEqualTo(department == null ? null : "jiraProjectKey");
                    assertThat(error.getParams())
                            .containsExactlyInAnyOrderEntriesOf(
                                    department == null
                                            ? Map.of("tracker_name", "Jira")
                                            : Map.of("tracker_name", "Jira", "department_name", department));
                });
    }

    private void assertCreateAccess(CreateProjectRequest request) {
        assertThatThrownBy(() -> service().create(request))
                .isInstanceOfSatisfying(ProjectAccessDeniedException.class, error -> {
                    assertThat(error.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
                    assertThat(error.getCode()).isEqualTo("MSG08");
                    assertThat(error.getField()).isNull();
                    assertThat(error.getParams()).isEmpty();
                });
    }

    private void assertBeforeJiraRefusal() {
        verifyNoInteractions(tracker);
        assertBeforeTransactionRefusal();
    }

    private void assertBeforeTransactionRefusal() {
        verify(transactionManager, never()).getTransaction(any());
        assertNoCreateWrites();
    }

    private void assertNoCreateWrites() {
        verify(links, never()).insertProject(anyLong(), anyString(), anyList(), anyInt());
        verifyNoInteractions(audit);
    }
}
