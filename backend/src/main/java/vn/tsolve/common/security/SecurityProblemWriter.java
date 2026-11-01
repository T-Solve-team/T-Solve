package vn.tsolve.common.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;

/**
 * Writes a security rejection as a ProblemDetail with only the MSG code and its SRS text — never the token or
 * the reason it failed (AC-001.7).
 */
public class SecurityProblemWriter {

    public static final String MSG06 = "MSG06";
    public static final String MSG08 = "MSG08";

    private static final String MSG06_TEXT = "Something went wrong. Please try again later.";
    private static final String MSG08_TEXT = "You do not have permission to access this page.";

    private final ObjectMapper objectMapper;

    public SecurityProblemWriter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public void write(HttpServletResponse response, HttpStatus status, String code) throws IOException {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, MSG06.equals(code) ? MSG06_TEXT : MSG08_TEXT);
        problem.setProperty("code", code);
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), problem);
    }
}
