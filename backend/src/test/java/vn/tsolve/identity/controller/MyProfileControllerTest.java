package vn.tsolve.identity.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import vn.tsolve.common.exception.GlobalExceptionHandler;
import vn.tsolve.common.security.Role;
import vn.tsolve.common.security.SecurityConfig;
import vn.tsolve.common.security.TSolveUser;
import vn.tsolve.common.security.WithTSolveUser;
import vn.tsolve.common.session.dto.MySolution;
import vn.tsolve.common.session.dto.ReviewDecision;
import vn.tsolve.common.session.dto.SpaceRef;
import vn.tsolve.common.session.dto.TicketStatus;
import vn.tsolve.identity.dto.MySolutionsQuery;
import vn.tsolve.identity.service.MyProfileService;

/**
 * GET /api/v1/me/spaces and GET /api/v1/me/solutions. The per-request Role re-read is tested by
 * RoleChangeTest, the cookie by WebSessionFilterTest and the Workspace binding by WorkspaceBindingTest
 * (aSessionReadsOnlyItsWorkspace_AC_001_76), all on main. They are not repeated here.
 */
@WebMvcTest(MyProfileController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
class MyProfileControllerTest {
    private static final String PATH = "/api/v1/me/spaces";
    private static final String SOLUTIONS = "/api/v1/me/solutions";
    private static final String USER_A = "00000000-0000-0000-0000-0000000000a1";
    private static final UUID USER_B = UUID.fromString("00000000-0000-0000-0000-0000000000b2");
    private static final UUID WORKSPACE = UUID.fromString("00000000-0000-0000-0000-0000000000c3");
    private static final UUID PROJECT = UUID.fromString("00000000-0000-0000-0000-0000000000d4");

    @Autowired
    private MockMvc mvc;

    @MockBean
    private MyProfileService service;

    @Test
    @WithTSolveUser(roles = Role.KNOWLEDGE_MANAGER)
    void answersTheServiceSpacesAsIs_AC_003_4() throws Exception {
        var first = new SpaceRef(UUID.fromString("00000000-0000-0000-0000-000000000011"), "An toàn");
        var second = new SpaceRef(UUID.fromString("00000000-0000-0000-0000-000000000022"), "Đào tạo");
        when(service.getMyViewableSpaces()).thenReturn(List.of(first, second));

        mvc.perform(get(PATH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].spaceId").value(first.spaceId().toString()))
                .andExpect(jsonPath("$[0].name").value("An toàn"))
                .andExpect(jsonPath("$[0].length()").value(2))
                .andExpect(jsonPath("$[1].spaceId").value(second.spaceId().toString()))
                .andExpect(jsonPath("$[1].name").value("Đào tạo"))
                .andExpect(jsonPath("$[1].length()").value(2));
    }

    @Test
    @WithTSolveUser
    void allowsAnEmployeeWithNoOtherRole_AC_003_10() throws Exception {
        when(service.getMyViewableSpaces()).thenReturn(List.of());

        mvc.perform(get(PATH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));

        verify(service, times(1)).getMyViewableSpaces();
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                PATH,
                PATH + "?userId=00000000-0000-0000-0000-0000000000b2&workspaceId=00000000-0000-0000-0000-0000000000c3"
            })
    void refusesAnonymous_AC_003_10(String url) throws Exception {
        mvc.perform(get(url))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("MSG07"));

        verifyNoInteractions(service);
    }

    @Test
    @WithTSolveUser(id = USER_A)
    void ignoresUserIdAndWorkspaceIdParameters_AC_003_7() throws Exception {
        var own = new SpaceRef(UUID.fromString("00000000-0000-0000-0000-0000000000a9"), "Space of A");
        when(service.getMyViewableSpaces()).thenReturn(List.of(own));

        mvc.perform(get(PATH).param("userId", USER_B.toString()).param("workspaceId", WORKSPACE.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].spaceId").value(own.spaceId().toString()))
                .andExpect(jsonPath("$[0].name").value("Space of A"));

        verify(service, times(1)).getMyViewableSpaces();
        verifyNoMoreInteractions(service);
    }

    @Test
    @WithTSolveUser
    void answers500WithoutDetailWhenTheLookupFails_AC_003_4() throws Exception {
        when(service.getMyViewableSpaces()).thenThrow(new IllegalStateException("wiki db down for ada@example.test"));

        mvc.perform(get(PATH))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("MSG06"))
                .andExpect(jsonPath("$.detail").doesNotExist())
                .andExpect(content().string(not(containsString("ada@example.test"))))
                .andExpect(content().string(not(containsString("IllegalStateException"))));
    }

    private static MySolution solution(String key, MySolution.LatestDecision decision) {
        return new MySolution(
                UUID.fromString("00000000-0000-0000-0000-0000000000e5"),
                key,
                "Sửa lỗi in",
                PROJECT,
                "Dự án A",
                TicketStatus.PUBLISHED,
                Instant.parse("2026-01-15T03:00:00Z"),
                true,
                decision);
    }

    @Test
    @WithTSolveUser(roles = Role.TICKET_RESOLVER)
    void answersThePageForATicketResolver_AC_003_6() throws Exception {
        var decision = new MySolution.LatestDecision(
                ReviewDecision.REQUEST_CHANGES, "Thêm bước 3", Instant.parse("2026-01-16T04:30:00Z"));
        var page = new PageImpl<>(
                List.of(solution("KAN-1", decision), solution("KAN-2", null)), PageRequest.of(0, 100), 2);
        when(service.getMySolutions(any())).thenReturn(page);

        mvc.perform(get(SOLUTIONS)
                        .param("page", "0")
                        .param("size", "100")
                        .param("status", "PUBLISHED")
                        .param("projectId", PROJECT.toString())
                        .param("from", "2026-01-01")
                        .param("to", "2026-01-31"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.page.size").value(100))
                .andExpect(jsonPath("$.page.number").value(0))
                .andExpect(jsonPath("$.page.totalElements").value(2))
                .andExpect(jsonPath("$.page.totalPages").value(1))
                .andExpect(jsonPath("$.content[0].length()").value(9))
                .andExpect(jsonPath("$.content[0].ticketId").value("00000000-0000-0000-0000-0000000000e5"))
                .andExpect(jsonPath("$.content[0].ticketKey").value("KAN-1"))
                .andExpect(jsonPath("$.content[0].title").value("Sửa lỗi in"))
                .andExpect(jsonPath("$.content[0].projectId").value(PROJECT.toString()))
                .andExpect(jsonPath("$.content[0].projectName").value("Dự án A"))
                .andExpect(jsonPath("$.content[0].status").value("PUBLISHED"))
                .andExpect(jsonPath("$.content[0].updatedAt").value("2026-01-15T03:00:00Z"))
                .andExpect(jsonPath("$.content[0].canOpen").value(true))
                .andExpect(jsonPath("$.content[0].latestDecision.action").value("REQUEST_CHANGES"))
                .andExpect(jsonPath("$.content[0].latestDecision.comment").value("Thêm bước 3"))
                .andExpect(jsonPath("$.content[0].latestDecision.decidedAt").value("2026-01-16T04:30:00Z"))
                .andExpect(jsonPath("$.content[1]", hasKey("latestDecision")))
                .andExpect(jsonPath("$.content[1].latestDecision").value(nullValue()));

        var captor = ArgumentCaptor.forClass(MySolutionsQuery.class);
        verify(service).getMySolutions(captor.capture());
        assertThat(captor.getValue())
                .isEqualTo(new MySolutionsQuery(
                        0, 100, TicketStatus.PUBLISHED, PROJECT, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31)));
    }

    @Test
    @WithTSolveUser(roles = Role.PROJECT_MANAGER)
    void allowsAProjectManagerAndAPagePastTheEnd_AC_003_6() throws Exception {
        when(service.getMySolutions(any())).thenReturn(new PageImpl<MySolution>(List.of(), PageRequest.of(9, 1), 5));

        mvc.perform(get(SOLUTIONS).param("page", "9").param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(0))
                .andExpect(jsonPath("$.page.number").value(9))
                .andExpect(jsonPath("$.page.size").value(1))
                .andExpect(jsonPath("$.page.totalElements").value(5))
                .andExpect(jsonPath("$.page.totalPages").value(5));

        verify(service, times(1)).getMySolutions(any());
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                SOLUTIONS,
                SOLUTIONS + "?size=101",
                SOLUTIONS + "?status=FOO",
                SOLUTIONS
                        + "?userId=00000000-0000-0000-0000-0000000000b2&workspaceId=00000000-0000-0000-0000-0000000000c3"
            })
    void refusesAnonymousOnMySolutions_AC_003_10(String url) throws Exception {
        mvc.perform(get(url))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("MSG07"));

        verifyNoInteractions(service);
    }

    static Stream<Set<Role>> rolesWithoutTicketResolver() {
        return Stream.of(Set.of(), Set.of(Role.KNOWLEDGE_MANAGER), Set.of(Role.ADMIN));
    }

    @ParameterizedTest
    @MethodSource("rolesWithoutTicketResolver")
    void refusesASignedInUserWithoutTheRole_AC_003_17(Set<Role> roles) throws Exception {
        // The per-request Role re-read between two calls is RoleChangeTest on main.
        var user = new TSolveUser(UUID.fromString(USER_A), "user@example.test", "Test User", roles);

        mvc.perform(get(SOLUTIONS)
                        .param("page", "0")
                        .param("size", "20")
                        .with(authentication(new UsernamePasswordAuthenticationToken(user, null, user.authorities()))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("MSG08"));

        verifyNoInteractions(service);
    }

    @ParameterizedTest
    @CsvSource({
        "page, -1",
        "size, 0",
        "size, 101",
        "status, FOO",
        "status, published",
        "projectId, abc",
        "from, 2026-13-01",
        "from, 2026-02-30",
        "to, 31/10/2026",
        "page, abc",
        "size, 1.5"
    })
    @WithTSolveUser(roles = Role.TICKET_RESOLVER)
    void rejectsAWrongParameter_AC_003_9(String field, String value) throws Exception {
        mvc.perform(get(SOLUTIONS).param(field, value))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MSG06"))
                .andExpect(jsonPath("$.fieldErrors.length()").value(1))
                .andExpect(jsonPath("$.fieldErrors[0].field").value(field))
                .andExpect(jsonPath("$.fieldErrors[0].code").value("MSG06"));

        verifyNoInteractions(service);
    }

    @Test
    @WithTSolveUser(roles = Role.TICKET_RESOLVER)
    void reportsTheRangeErrorOnToAndAcceptsOneDay_AC_003_9() throws Exception {
        when(service.getMySolutions(any())).thenReturn(new PageImpl<MySolution>(List.of()));

        mvc.perform(get(SOLUTIONS).param("from", "2026-02-01").param("to", "2026-01-31"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MSG06"))
                .andExpect(jsonPath("$.fieldErrors.length()").value(1))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("to"))
                .andExpect(jsonPath("$.fieldErrors[0].code").value("MSG06"));
        verifyNoInteractions(service);

        mvc.perform(get(SOLUTIONS).param("from", "2026-01-15").param("to", "2026-01-15"))
                .andExpect(status().isOk());
        mvc.perform(get(SOLUTIONS).param("to", "2026-01-31")).andExpect(status().isOk());
    }

    @ParameterizedTest
    @CsvSource(
            delimiter = ';',
            value = {
                "status=FOO&size=101;size,status",
                "page=-1&from=2026-02-01&to=2026-01-31;page,to",
                "projectId=ada@example.test&from=2026-13-01;from,projectId"
            })
    @WithTSolveUser(roles = Role.TICKET_RESOLVER)
    void givesOneEntryPerWrongParameter_AC_003_9(String query, String fields) throws Exception {
        var expected = fields.split(",");

        var result = mvc.perform(get(SOLUTIONS + "?" + query))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MSG06"))
                .andExpect(jsonPath("$.fieldErrors.length()").value(expected.length));
        for (int i = 0; i < expected.length; i++) {
            result.andExpect(jsonPath("$.fieldErrors[" + i + "].field").value(expected[i]))
                    .andExpect(jsonPath("$.fieldErrors[" + i + "].code").value("MSG06"));
        }
        result.andExpect(content().string(not(containsString("ada@example.test"))));

        verifyNoInteractions(service);
    }

    @Test
    @WithTSolveUser(id = USER_A, roles = Role.TICKET_RESOLVER)
    void ignoresUserIdAndWorkspaceIdParametersOnMySolutions_AC_003_7() throws Exception {
        var own = solution("KAN-7", null);
        when(service.getMySolutions(any())).thenReturn(new PageImpl<>(List.of(own), PageRequest.of(0, 20), 1));

        mvc.perform(get(SOLUTIONS).param("userId", USER_B.toString()).param("workspaceId", WORKSPACE.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].ticketKey").value("KAN-7"));

        var captor = ArgumentCaptor.forClass(MySolutionsQuery.class);
        verify(service, times(1)).getMySolutions(captor.capture());
        verifyNoMoreInteractions(service);
        assertThat(captor.getValue()).isEqualTo(new MySolutionsQuery(null, null, null, null, null, null));
    }
}
