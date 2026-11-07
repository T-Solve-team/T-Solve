package vn.tsolve.ai.exception;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.tsolve.common.exception.GlobalExceptionHandler;

class AiRewriteUnavailableExceptionTest {

    @RestController
    static class AiCallingController {
        @GetMapping("/test/ai-rewrite")
        void callAi() {
            throw new AiRewriteUnavailableException();
        }
    }

    @Test
    @DisplayName("AC-005.3 an uncaught rewrite failure answers 503 with MSG68 and carries no content")
    void uncaughtRewriteFailureIs503Msg68() throws Exception {
        AiRewriteUnavailableException failure = new AiRewriteUnavailableException();
        assertThat(failure.getCause()).isNull();

        MockMvc mvc = MockMvcBuilders.standaloneSetup(new AiCallingController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        mvc.perform(get("/test/ai-rewrite"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("MSG68"));
    }
}
