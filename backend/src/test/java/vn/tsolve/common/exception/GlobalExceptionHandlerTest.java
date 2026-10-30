package vn.tsolve.common.exception;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.access.ExceptionTranslationFilter;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

@ExtendWith(OutputCaptureExtension.class)
class GlobalExceptionHandlerTest {

    private static final String PERSONAL_DATA = "jane.doe@example.com";

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new FailingController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void answersNotFoundAsProblemDetailWithCode_AC_001_1_BUG_001() throws Exception {
        mvc.perform(get("/test/not-found"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.code").value("MSG110"))
                .andExpect(jsonPath("$.params.ticket_id").value("KAN-7"));
    }

    @Test
    void answersBusinessRuleWith409CodeAndParams_AC_001_2() throws Exception {
        mvc.perform(get("/test/rule"))
                .andExpect(status().isConflict())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.code").value("MSG49"))
                .andExpect(jsonPath("$.params.department_name").value("IT"));
    }

    @Test
    void putsTheNamedFieldInFieldErrors_AC_001_3() throws Exception {
        mvc.perform(get("/test/rule-field"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("MSG11"))
                .andExpect(jsonPath("$.fieldErrors.length()").value(1))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("email"))
                .andExpect(jsonPath("$.fieldErrors[0].code").value("MSG11"))
                .andExpect(jsonPath("$.fieldErrors[0].params").isMap());
    }

    @Test
    void mapsAMissingRequiredValueToMsg01_AC_001_4() throws Exception {
        postForm("{\"name\":\"\"}")
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.fieldErrors.length()").value(1))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("name"))
                .andExpect(jsonPath("$.fieldErrors[0].code").value("MSG01"))
                .andExpect(jsonPath("$.fieldErrors[0].params.field_name").value("name"))
                .andExpect(jsonPath("$.code").value("MSG01"));
    }

    @Test
    void mapsAValueOverItsLengthLimitToMsg02WithMaxLength_AC_001_4() throws Exception {
        postForm("{\"name\":\"" + "a".repeat(151) + "\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("name"))
                .andExpect(jsonPath("$.fieldErrors[0].code").value("MSG02"))
                .andExpect(jsonPath("$.fieldErrors[0].params.field_name").value("name"))
                .andExpect(jsonPath("$.fieldErrors[0].params.max_length").value(150));
    }

    @Test
    void mapsAnInvalidEmailToMsg03_AC_001_4() throws Exception {
        postForm("{\"name\":\"Jane\",\"email\":\"not-an-email\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.length()").value(1))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("email"))
                .andExpect(jsonPath("$.fieldErrors[0].code").value("MSG03"));
    }

    @Test
    void givesOneEntryPerFieldAndMsg01WinsOnTheSameField_AC_001_4() throws Exception {
        // 151 spaces fail both @NotBlank and @Size on "name"; "email" fails on its own.
        postForm("{\"name\":\"" + " ".repeat(151) + "\",\"email\":\"x\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.length()").value(2))
                .andExpect(jsonPath("$.fieldErrors[?(@.field == 'name')].code").value("MSG01"))
                .andExpect(jsonPath("$.fieldErrors[?(@.field == 'email')].code").value("MSG03"));
    }

    @Test
    void mapsAnyOtherFailedRuleToMsg06_AC_001_5() throws Exception {
        postForm("{\"name\":\"Jane\",\"key\":\"lower\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.length()").value(1))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("key"))
                .andExpect(jsonPath("$.fieldErrors[0].code").value("MSG06"));
    }

    @Test
    void answersABusinessRuleWithoutCodeWithItsMessageInDetailOnly_AC_001_6() throws Exception {
        mvc.perform(get("/test/legacy-rule"))
                .andExpect(status().isConflict())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$", not(hasKey("code"))))
                .andExpect(jsonPath("$.detail").value("Cannot move solution"));
    }

    @Test
    void answersANotFoundWithoutCodeWithMsg04AndItsMessageInDetail_AC_001_10() throws Exception {
        mvc.perform(get("/test/legacy"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("MSG04"))
                .andExpect(jsonPath("$.params").isEmpty())
                .andExpect(jsonPath("$.detail").value("Solution 7 not found"));
    }

    @Test
    void answersAMessageCodeExceptionWithItsOwnStatusCodeAndParams_AC_001_11() throws Exception {
        mvc.perform(get("/test/message-code"))
                .andExpect(status().isConflict())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.code").value("MSG30"))
                .andExpect(jsonPath("$.params.count").value(3));
    }

    @Test
    void answersAnUploadOverTheSizeLimitWith413Msg28_AC_001_12() throws Exception {
        mvc.perform(get("/test/too-large"))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("MSG28"));
    }

    @Test
    void answersAnUnknownExceptionWith500Msg06NoDetailAndLogsIt_AC_001_7(CapturedOutput output) throws Exception {
        String body = mvc.perform(get("/test/boom"))
                .andExpect(status().isInternalServerError())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("MSG06"))
                .andExpect(jsonPath("$", not(hasKey("detail"))))
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(body).doesNotContain(PERSONAL_DATA).doesNotContain("IllegalStateException");
        assertThat(output).contains("Unhandled exception").contains(PERSONAL_DATA);
    }

    @Test
    void answersAMalformedBodyWithSpringStatusAndMsg06_AC_001_8() throws Exception {
        postForm("{not json")
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("MSG06"));
    }

    @Test
    void answersAWrongMethodWithSpringStatusAndMsg06_AC_001_8() throws Exception {
        mvc.perform(delete("/test/rule"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.code").value("MSG06"));
    }

    @Test
    void answersAnUnsupportedMediaTypeWithSpringStatusAndMsg06_AC_001_8() throws Exception {
        mvc.perform(post("/test/form").contentType(MediaType.TEXT_PLAIN).content("name=Jane"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code").value("MSG06"));
    }

    @Test
    void answersAMissingParameterWithSpringStatusAndMsg06_AC_001_8() throws Exception {
        mvc.perform(get("/test/param"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MSG06"));
    }

    @Test
    void answersAnUnknownPathWith404Msg06_AC_001_8() throws Exception {
        mvc.perform(get("/test/no-such-path"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("MSG06"));
    }

    @Test
    void keepsTheBaseExceptionsInCommonExceptionWithTheirMessageOnlyConstructors_AC_001_9() {
        assertThat(List.of(NotFoundException.class, BusinessRuleException.class, MessageCodeException.class))
                .extracting(Class::getPackageName)
                .containsOnly("vn.tsolve.common.exception");
        assertThat(new BusinessRuleException("Cannot move solution").getMessage())
                .isEqualTo("Cannot move solution");
        for (String name : List.of(
                "NotFoundException", "BusinessRuleException", "MessageCodeException", "GlobalExceptionHandler")) {
            assertThatThrownBy(() -> Class.forName("vn.tsolve.common.error." + name))
                    .isInstanceOf(ClassNotFoundException.class);
        }
    }

    @Test
    void answersAccessDeniedInsideTheDispatcherWith403Msg08_AC_002_5() throws Exception {
        mvc.perform(get("/test/denied"))
                .andExpect(status().isForbidden())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("MSG08"));
    }

    @Test
    void letsAMissingAuthenticationReachTheEntryPoint_AC_002_4_BUG_061() throws Exception {
        securedMvc().perform(get("/test/no-authentication")).andExpect(status().isUnauthorized());
    }

    @Test
    void letsAnAnonymousUsersDenialReachTheEntryPoint_AC_002_4_BUG_062() throws Exception {
        SecurityContextHolder.getContext()
                .setAuthentication(new AnonymousAuthenticationToken(
                        "key", "anonymousUser", AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS")));

        securedMvc().perform(get("/test/denied")).andExpect(status().isUnauthorized());
    }

    @Test
    void answersASignedInUsersDenialWith403Msg08ThroughTheFilterChain_AC_002_5() throws Exception {
        SecurityContextHolder.getContext()
                .setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                        "jane", null, AuthorityUtils.createAuthorityList("ROLE_EMPLOYEE")));

        securedMvc()
                .perform(get("/test/denied"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("MSG08"));
    }

    // Spring Security's ExceptionTranslationFilter in front, as in the application, so the advice's interplay with it
    // is tested: what the filter would send to sign-in must get past the advice.
    private MockMvc securedMvc() {
        return MockMvcBuilders.standaloneSetup(new FailingController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .addFilters(new ExceptionTranslationFilter(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
                .build();
    }

    private ResultActions postForm(String json) throws Exception {
        return mvc.perform(
                post("/test/form").contentType(MediaType.APPLICATION_JSON).content(json));
    }

    record TestForm(
            @NotBlank @Size(max = 150) String name, @Email String email, @Pattern(regexp = "[A-Z]+") String key) {}

    @RestController
    static class FailingController {

        @GetMapping("/test/not-found")
        void notFound() {
            throw new NotFoundException("MSG110", Map.of("ticket_id", "KAN-7"));
        }

        @GetMapping("/test/rule")
        void rule() {
            throw new BusinessRuleException("MSG49", Map.of("department_name", "IT"));
        }

        @GetMapping("/test/rule-field")
        void ruleWithField() {
            throw new BusinessRuleException("MSG11", Map.of(), "email");
        }

        @PostMapping("/test/form")
        void form(@Valid @RequestBody TestForm form) {}

        @GetMapping("/test/legacy")
        void legacy() {
            throw new NotFoundException("Solution 7 not found");
        }

        @GetMapping("/test/legacy-rule")
        void legacyRule() {
            throw new BusinessRuleException("Cannot move solution");
        }

        @GetMapping("/test/message-code")
        void messageCode() {
            throw new MessageCodeException(HttpStatus.CONFLICT, "MSG30", Map.of("count", 3)) {};
        }

        @GetMapping("/test/too-large")
        void tooLarge() {
            throw new MaxUploadSizeExceededException(10L * 1024 * 1024);
        }

        @GetMapping("/test/boom")
        void boom() {
            throw new IllegalStateException("Lookup failed for " + PERSONAL_DATA);
        }

        @GetMapping("/test/param")
        void param(@RequestParam String required) {}

        @GetMapping("/test/denied")
        void denied() {
            throw new AccessDeniedException("Access Denied");
        }

        // What method security throws when the security context holds no Authentication at all.
        @GetMapping("/test/no-authentication")
        void noAuthentication() {
            throw new AuthenticationCredentialsNotFoundException("No Authentication");
        }
    }
}
