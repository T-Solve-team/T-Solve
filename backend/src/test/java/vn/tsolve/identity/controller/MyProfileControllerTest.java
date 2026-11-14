package vn.tsolve.identity.controller;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import vn.tsolve.common.exception.GlobalExceptionHandler;
import vn.tsolve.common.security.Role;
import vn.tsolve.common.security.SecurityConfig;
import vn.tsolve.common.security.WithTSolveUser;
import vn.tsolve.common.session.dto.SpaceRef;
import vn.tsolve.identity.service.MyProfileService;

/**
 * GET /api/v1/me/spaces. The per-request Role re-read is tested by RoleChangeTest, the cookie by
 * WebSessionFilterTest and the Workspace binding by WorkspaceBindingTest
 * (aSessionReadsOnlyItsWorkspace_AC_001_76), all on main. They are not repeated here.
 */
@WebMvcTest(MyProfileController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
class MyProfileControllerTest {
    private static final String PATH = "/api/v1/me/spaces";
    private static final String USER_A = "00000000-0000-0000-0000-0000000000a1";
    private static final UUID USER_B = UUID.fromString("00000000-0000-0000-0000-0000000000b2");
    private static final UUID WORKSPACE = UUID.fromString("00000000-0000-0000-0000-0000000000c3");

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
}
