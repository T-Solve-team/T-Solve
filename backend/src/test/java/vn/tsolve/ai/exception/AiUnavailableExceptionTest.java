package vn.tsolve.ai.exception;

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

class AiUnavailableExceptionTest {

    @RestController
    static class AiCallingController {
        @GetMapping("/test/ai")
        void callAi() {
            throw new AiUnavailableException();
        }
    }

    @Test
    @DisplayName("AC-003.8 an AI failure nobody catches answers 503 with code MSG06")
    void uncaughtFailureIs503Msg06() throws Exception {
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new AiCallingController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        mvc.perform(get("/test/ai"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("MSG06"));
    }
}
