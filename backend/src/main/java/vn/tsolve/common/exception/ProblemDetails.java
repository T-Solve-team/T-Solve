package vn.tsolve.common.exception;

import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;

/** The one error body of the backend: an RFC 9457 {@link ProblemDetail} with the SRS message code. */
public final class ProblemDetails {

    /** "Something went wrong" — also what the web app shows for a missing or unknown code. */
    public static final String UNEXPECTED_ERROR = "MSG06";

    private ProblemDetails() {}

    /** One entry of {@code fieldErrors}: the message shown below a form field. */
    public record FieldError(String field, String code, Map<String, ?> params) {}

    public static ProblemDetail of(HttpStatusCode status, String code, Map<String, ?> params) {
        return withCode(ProblemDetail.forStatus(status), code, params);
    }

    public static ProblemDetail withCode(ProblemDetail problem, String code, Map<String, ?> params) {
        problem.setProperty("code", code);
        problem.setProperty("params", params);
        return problem;
    }

    public static ProblemDetail withFieldErrors(ProblemDetail problem, List<FieldError> fieldErrors) {
        problem.setProperty("fieldErrors", fieldErrors);
        return problem;
    }

    static boolean hasCode(ProblemDetail problem) {
        return problem.getProperties() != null && problem.getProperties().containsKey("code");
    }
}
