package vn.tsolve.review.controller;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Optional;
import java.util.Set;
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
import vn.tsolve.review.dto.AiReviewFinding;
import vn.tsolve.review.dto.AiReviewFinding.Field;
import vn.tsolve.review.dto.AiReviewFinding.Severity;
import vn.tsolve.review.dto.AiReviewResult;
import vn.tsolve.review.exception.AiReviewUnavailableException;
import vn.tsolve.review.service.AiReviewService;
import vn.tsolve.review.service.ReviewWorkspaceAccess;

@WebMvcTest(
        useDefaultFilters = false,
        includeFilters =
                @ComponentScan.Filter(
                        type = FilterType.REGEX,
                        pattern = "vn.tsolve.review.controller.AiReviewController"))
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
class AiReviewControllerTest {
    private static final UUID ACCOUNT = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID TICKET = UUID.fromString("30000000-0000-0000-0000-000000000001");
    private static final Set<UUID> MANAGED = Set.of(UUID.fromString("40000000-0000-0000-0000-000000000001"));
    private static final String PATH = "/api/v1/tickets/{ticketId}/ai-review";

    @Autowired
    private MockMvc mvc;

    @MockBean
    private AiReviewService reviews;

    @MockBean
    private ReviewWorkspaceAccess access;

    @Test
    @WithTSolveUser(roles = Role.PROJECT_MANAGER)
    void returnsTheFindings_AC_015_4() throws Exception {
        when(access.managedProjectIds(ACCOUNT)).thenReturn(Optional.of(MANAGED));
        when(reviews.review(MANAGED, TICKET))
                .thenReturn(new AiReviewResult(List.of(
                        new AiReviewFinding(Field.ROOT_CAUSE, Severity.HIGH, "Does not explain it", "Say why"))));

        mvc.perform(post(PATH, TICKET).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.findings[0].field").value("ROOT_CAUSE"))
                .andExpect(jsonPath("$.findings[0].severity").value("HIGH"))
                .andExpect(jsonPath("$.findings[0].issue").value("Does not explain it"))
                .andExpect(jsonPath("$.findings[0].suggestion").value("Say why"));
    }

    @Test
    @WithTSolveUser(roles = Role.PROJECT_MANAGER)
    void answers503WithItsCode_AC_015_6() throws Exception {
        when(access.managedProjectIds(ACCOUNT)).thenReturn(Optional.of(MANAGED));
        when(reviews.review(MANAGED, TICKET)).thenThrow(new AiReviewUnavailableException());

        mvc.perform(post(PATH, TICKET).with(csrf()))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("MSG_NEW_AI_REVIEW_1"));
    }

    @Test
    void refusesAnUnauthenticatedCallerWith401_AC_015_7() throws Exception {
        mvc.perform(post(PATH, TICKET).with(csrf())).andExpect(status().isUnauthorized());
        verifyNoInteractions(reviews);
    }

    @Test
    @WithTSolveUser(roles = Role.TICKET_RESOLVER)
    void refusesACallerWithoutProjectManagerWith403_AC_015_7() throws Exception {
        mvc.perform(post(PATH, TICKET).with(csrf())).andExpect(status().isForbidden());
        verifyNoInteractions(reviews);
    }

    @Test
    @WithTSolveUser(roles = Role.PROJECT_MANAGER)
    void refusesAnUnverifiedWorkspaceAccountWith403_AC_015_7() throws Exception {
        when(access.managedProjectIds(ACCOUNT)).thenReturn(Optional.empty());

        mvc.perform(post(PATH, TICKET).with(csrf())).andExpect(status().isForbidden());
        verifyNoInteractions(reviews);
    }
}
