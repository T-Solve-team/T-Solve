package vn.tsolve.chatbot.source.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
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
import vn.tsolve.common.security.Role;
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
    private static final UUID FIRST_ID = UUID.fromString("70000000-0000-0000-0000-00000000000a");
    private static final UUID SECOND_ID = UUID.fromString("70000000-0000-0000-0000-000000000002");
    private static final UUID THIRD_ID = UUID.fromString("70000000-0000-0000-0000-000000000003");
    private static final UUID EXTRA_ID = UUID.fromString("70000000-0000-0000-0000-000000000004");

    @Autowired
    private MockMvc mvc;

    @MockBean
    private WikiSources sources;

    @ParameterizedTest
    @EnumSource(Role.class)
    void listsWhatThePortReturns_AC_006_5(Role role) throws Exception {
        TSolveUser user = new TSolveUser(EXTRA_ID, "reader@example.test", "Reader", Set.of(role));
        when(sources.unavailable(any(), any())).thenReturn(new LinkedHashSet<>(List.of(THIRD_ID, EXTRA_ID, FIRST_ID)));

        mvc.perform(get(PATH)
                        .with(authentication(new UsernamePasswordAuthenticationToken(user, null, user.authorities())))
                        .param(
                                "wikiDocumentIds",
                                FIRST_ID.toString(),
                                SECOND_ID.toString(),
                                FIRST_ID.toString().toUpperCase(Locale.ROOT),
                                THIRD_ID.toString(),
                                SECOND_ID.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.wikiDocumentIds").value(contains(FIRST_ID.toString(), THIRD_ID.toString())));

        ArgumentCaptor<Set<UUID>> ids = ArgumentCaptor.captor();
        verify(sources).unavailable(same(user), ids.capture());
        assertThat(ids.getValue()).containsExactly(FIRST_ID, SECOND_ID, THIRD_ID);
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "missing",
                "zero",
                "empty",
                "tooMany",
                "tooManyRepeated",
                "bad",
                "shortUuid",
                "whitespace",
                "mixedEmpty",
                "one",
                "fifty"
            })
    @WithTSolveUser
    void rejectsMissingTooManyOrBadIds_AC_006_6(String input) throws Exception {
        String[] values =
                switch (input) {
                    case "zero" -> new String[0];
                    case "empty" -> new String[] {""};
                    case "tooMany" -> IntStream.rangeClosed(1, 51)
                            .mapToObj(i -> new UUID(0, i).toString())
                            .toArray(String[]::new);
                        // The limit counts supplied values before deduplication, including repeated ids.
                    case "tooManyRepeated" -> Collections.nCopies(51, FIRST_ID.toString())
                            .toArray(String[]::new);
                    case "bad" -> new String[] {FIRST_ID.toString(), "not-a-uuid"};
                    case "shortUuid" -> new String[] {"1-1-1-1-1"};
                    case "whitespace" -> new String[] {" "};
                    case "mixedEmpty" -> new String[] {FIRST_ID.toString(), ""};
                    case "one" -> new String[] {FIRST_ID.toString()};
                    case "fifty" -> IntStream.rangeClosed(1, 50)
                            .mapToObj(i -> new UUID(0, i).toString())
                            .toArray(String[]::new);
                    default -> null;
                };
        MockHttpServletRequestBuilder request = get(PATH);
        if (values != null && values.length == 0) {
            request.with(servletRequest -> {
                servletRequest.setParameter("wikiDocumentIds", values);
                return servletRequest;
            });
        } else if (values != null) {
            request.param("wikiDocumentIds", values);
        }

        if (input.equals("one") || input.equals("fifty")) {
            when(sources.unavailable(any(), any())).thenAnswer(call -> call.getArgument(1));
            mvc.perform(request)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.wikiDocumentIds").value(contains(values)));
            verify(sources).unavailable(any(), any());
        } else {
            mvc.perform(request)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("MSG06"))
                    .andExpect(jsonPath("$.fieldErrors").doesNotExist());
            verifyNoInteractions(sources);
        }
    }

    @Test
    void answers401WithoutSession_AC_006_6() throws Exception {
        mvc.perform(get(PATH).param("wikiDocumentIds", FIRST_ID.toString()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("MSG07"));
        verifyNoInteractions(sources);
    }
}
