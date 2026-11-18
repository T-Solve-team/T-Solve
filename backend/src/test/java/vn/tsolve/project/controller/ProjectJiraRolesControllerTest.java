package vn.tsolve.project.controller;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import vn.tsolve.common.exception.GlobalExceptionHandler;
import vn.tsolve.common.security.Role;
import vn.tsolve.common.security.SecurityConfig;
import vn.tsolve.common.security.WithTSolveUser;
import vn.tsolve.project.dto.JiraProjectRoleResponse;
import vn.tsolve.project.dto.JiraRolePeopleResponse;
import vn.tsolve.project.dto.JiraRolePersonResponse;
import vn.tsolve.project.dto.JiraRolePersonResponse.Status;
import vn.tsolve.project.exception.ProjectTrackerUnavailableException;
import vn.tsolve.project.service.ProjectMemberService;

@WebMvcTest(
        useDefaultFilters = false,
        includeFilters =
                @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = ProjectMemberController.class))
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
class ProjectJiraRolesControllerTest {
    private static final UUID PROJECT = new UUID(127, 10);
    private static final UUID USER = new UUID(127, 20);
    private static final String ROLES = "/api/v1/projects/" + PROJECT + "/jira-roles";

    @Autowired
    private MockMvc mvc;

    @MockBean
    private ProjectMemberService service;

    @Test
    @WithTSolveUser(roles = Role.PROJECT_MANAGER)
    void answersTheRoles_AC_011_1() throws Exception {
        when(service.jiraRoles(PROJECT)).thenReturn(List.of(new JiraProjectRoleResponse(10015, "Member")));

        mvc.perform(get(ROLES))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(10015))
                .andExpect(jsonPath("$[0].name").value("Member"));
    }

    @Test
    @WithTSolveUser(roles = Role.PROJECT_MANAGER)
    void answersThePeopleWithTSolveFieldsOnlyWhenAddable_AC_011_2() throws Exception {
        when(service.jiraRolePeople(PROJECT, 10015))
                .thenReturn(new JiraRolePeopleResponse(
                        List.of(
                                JiraRolePersonResponse.addable("j-1", "An", USER, "An Nguyen", "an@acme.example"),
                                JiraRolePersonResponse.notAddable("j-2", "Binh", Status.NOT_LINKED)),
                        List.of("it-support-team")));

        mvc.perform(get(ROLES + "/10015/people"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.people[0].status").value("ADDABLE"))
                .andExpect(jsonPath("$.people[0].userId").value(USER.toString()))
                .andExpect(jsonPath("$.people[1].status").value("NOT_LINKED"))
                .andExpect(jsonPath("$.people[1].userId").doesNotExist())
                .andExpect(jsonPath("$.groups[0]").value("it-support-team"));
    }

    @Test
    @WithTSolveUser(roles = Role.PROJECT_MANAGER)
    void answers503Msg06WhenJiraIsNotAvailable_AC_011_4() throws Exception {
        when(service.jiraRoles(PROJECT)).thenThrow(new ProjectTrackerUnavailableException());

        mvc.perform(get(ROLES))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("MSG06"));
    }

    @Test
    @WithTSolveUser
    void refusesACallerWithoutTheProjectManagerRole_AC_011_5() throws Exception {
        mvc.perform(get(ROLES)).andExpect(status().isForbidden());
        mvc.perform(get(ROLES + "/10015/people")).andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }
}
