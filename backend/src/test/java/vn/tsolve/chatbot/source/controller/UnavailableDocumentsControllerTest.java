package vn.tsolve.chatbot.source.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import vn.tsolve.chatbot.source.service.WikiSources;
import vn.tsolve.common.exception.GlobalExceptionHandler;
import vn.tsolve.common.security.SecurityConfig;
import vn.tsolve.common.security.TSolveUser;
import vn.tsolve.common.security.WithTSolveUser;

@WebMvcTest(
        useDefaultFilters = false,
        includeFilters =
                @ComponentScan.Filter(type = FilterType.REGEX, pattern = "vn.tsolve.chatbot.source.controller.*"))
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
class UnavailableDocumentsControllerTest {
    private static final String PATH = "/api/v1/chatbot/unavailable-documents";
    private static final String PARAM = "wikiDocumentIds";
    private static final UUID A = UUID.fromString("80000000-0000-0000-0000-00000000000a");
    private static final UUID B = UUID.fromString("80000000-0000-0000-0000-00000000000b");
    private static final UUID C = UUID.fromString("80000000-0000-0000-0000-00000000000c");
    private static final UUID NOT_GIVEN = UUID.fromString("80000000-0000-0000-0000-0000000000ff");

    @Autowired
    private MockMvc mvc;

    @MockBean
    private WikiSources wiki;

    @Test
    void listsWhatThePortReturns_AC_006_5() throws Exception {
        TSolveUser user = new TSolveUser(UUID.randomUUID(), "asker@example.test", "Asker", Set.of());
        when(wiki.unavailable(eq(user), any())).thenReturn(Set.of(C, A, NOT_GIVEN));

        mvc.perform(get(PATH)
                        .param(PARAM, C.toString(), A.toString(), B.toString(), A.toString())
                        .with(authentication(new UsernamePasswordAuthenticationToken(user, null, user.authorities()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.wikiDocumentIds.length()").value(2))
                .andExpect(jsonPath("$.wikiDocumentIds[0]").value(C.toString()))
                .andExpect(jsonPath("$.wikiDocumentIds[1]").value(A.toString()));

        ArgumentCaptor<Set<UUID>> asked = ArgumentCaptor.forClass(Set.class);
        verify(wiki).unavailable(eq(user), asked.capture());
        assertThat(asked.getValue()).containsExactly(C, A, B);
    }

    @Test
    @WithTSolveUser
    void acceptsFiftyIdsAfterDuplicates_AC_006_6() throws Exception {
        String[] ids = Stream.concat(fiftyIds().stream(), Stream.of(fiftyIds().getFirst()))
                .toArray(String[]::new);
        when(wiki.unavailable(any(), any())).thenReturn(Set.of());

        mvc.perform(get(PATH).param(PARAM, ids))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.wikiDocumentIds").isEmpty());
    }

    @ParameterizedTest
    @MethodSource("invalidRequests")
    @WithTSolveUser
    void rejectsMissingTooManyOrBadIds_AC_006_6(MockHttpServletRequestBuilder request) throws Exception {
        mvc.perform(request)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MSG06"))
                .andExpect(jsonPath("$.fieldErrors").doesNotExist());
        verifyNoInteractions(wiki);
    }

    @Test
    void answers401WithoutSession_AC_006_6() throws Exception {
        mvc.perform(get(PATH).param(PARAM, A.toString()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("MSG07"));
        verifyNoInteractions(wiki);
    }

    static Stream<Arguments> invalidRequests() {
        List<String> tooMany = Stream.concat(fiftyIds().stream(), Stream.of(NOT_GIVEN.toString()))
                .toList();
        return Stream.of(
                Arguments.of(get(PATH)),
                Arguments.of(get(PATH).param(PARAM, "")),
                Arguments.of(get(PATH).param(PARAM, tooMany.toArray(String[]::new))),
                Arguments.of(get(PATH).param(PARAM, A.toString(), "not-a-uuid")),
                Arguments.of(get(PATH).param(PARAM, "1-1-1-1-1")),
                Arguments.of(get(PATH).param(PARAM, A.toString(), "")));
    }

    private static List<String> fiftyIds() {
        return IntStream.rangeClosed(1, 50)
                .mapToObj(n -> String.format("90000000-0000-0000-0000-%012d", n))
                .toList();
    }
}
