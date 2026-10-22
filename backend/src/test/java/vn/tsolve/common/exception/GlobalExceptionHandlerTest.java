package vn.tsolve.common.exception;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

class GlobalExceptionHandlerTest {
    @Test
    void rendersSharedErrorsAsProblemDetail_BUG_023() throws Exception {
        var mvc = MockMvcBuilders.standaloneSetup(new FailureController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
        mvc.perform(get("/missing"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.code").value("MSG06"));
        mvc.perform(get("/conflict"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("MSG06"));
    }

    @Test
    void preservesReviewErrorMetadata_AC_003_3() throws Exception {
        var mvc = MockMvcBuilders.standaloneSetup(new FailureController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
        mvc.perform(get("/field"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MSG02"))
                .andExpect(jsonPath("$.params.max_length").value(1000))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("reason"))
                .andExpect(jsonPath("$.fieldErrors[0].code").value("MSG02"))
                .andExpect(jsonPath("$.fieldErrors[0].params.field_name").value("Reason"));
    }

    @Test
    void keepsFrameworkStatusAndHidesUnexpectedPayloads() throws Exception {
        var mvc = MockMvcBuilders.standaloneSetup(new FailureController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
        mvc.perform(get("/parameter"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MSG06"));
        mvc.perform(get("/unexpected"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("MSG06"))
                .andExpect(jsonPath("$.detail").doesNotExist())
                .andExpect(jsonPath("$.message").doesNotExist());
    }

    @RestController
    static class FailureController {
        @GetMapping("/field")
        public void field() {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST, "MSG02", "reason", Map.of("field_name", "Reason", "max_length", 1000));
        }

        @GetMapping("/parameter")
        public void parameter(@org.springframework.web.bind.annotation.RequestParam long id) {}

        @GetMapping("/unexpected")
        public void unexpected() {
            throw new IllegalStateException("private ticket payload");
        }

        @GetMapping("/missing")
        public void missing() {
            throw new NotFoundException("MSG06");
        }

        @GetMapping("/conflict")
        public void conflict() {
            throw new BusinessRuleException("MSG06");
        }
    }
}
