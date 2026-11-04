package vn.tsolve.common.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.json.ProblemDetailJacksonMixin;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class SecurityProblemWriterTest {
    @Test
    void includesCanonicalCodeAndParams_BUG_110() throws Exception {
        var mapper = new ObjectMapper().addMixIn(ProblemDetail.class, ProblemDetailJacksonMixin.class);
        var response = new MockHttpServletResponse();
        var request = new MockHttpServletRequest("GET", "/api/probe");
        request.setQueryString("token=private-test-token");
        new SecurityProblemWriter(mapper).write(request, response, HttpStatus.FORBIDDEN, "MSG08");
        var body = mapper.readTree(response.getContentAsByteArray());
        assertThat(body.path("code").asText()).isEqualTo("MSG08");
        assertThat(body.has("params")).isTrue();
        assertThat(body.path("params").size()).isZero();
        assertThat(body.path("instance").asText()).isEqualTo("/api/probe");
        assertThat(response.getContentAsString()).doesNotContain("private-test-token");
    }
}
