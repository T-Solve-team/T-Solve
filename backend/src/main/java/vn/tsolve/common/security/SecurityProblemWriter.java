package vn.tsolve.common.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import vn.tsolve.common.exception.ProblemDetails;

/**
 * Writes a security rejection as a ProblemDetail with only the MSG code and its SRS text — never the token or
 * the reason it failed (AC-001.7).
 */
public class SecurityProblemWriter {

    public static final String MSG06 = "MSG06";
    public static final String MSG07 = "MSG07";
    public static final String MSG08 = "MSG08";
    public static final String MSG10 = "MSG10";
    public static final String MSG123 = "MSG123";

    private static final String MSG06_TEXT = "Something went wrong. Please try again later.";
    private static final String MSG07_TEXT = "Your session has expired. Please log in again.";
    private static final String MSG08_TEXT = "You do not have permission to access this page.";
    private static final String MSG10_TEXT = "Your T-Solve account has been deactivated. Please contact your Admin.";
    private static final String MSG123_TEXT = "Your session is not valid on this site. Please log in again.";

    private final ObjectMapper objectMapper;

    public SecurityProblemWriter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public void write(HttpServletRequest request, HttpServletResponse response, HttpStatus status, String code)
            throws IOException {
        String detail =
                switch (code) {
                    case MSG07 -> MSG07_TEXT;
                    case MSG08 -> MSG08_TEXT;
                    case MSG10 -> MSG10_TEXT;
                    case MSG123 -> MSG123_TEXT;
                    default -> MSG06_TEXT;
                };
        ProblemDetail problem =
                ProblemDetails.withCode(ProblemDetail.forStatusAndDetail(status, detail), code, Map.of());
        problem.setInstance(URI.create(request.getRequestURI()));
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), problem);
    }
}
