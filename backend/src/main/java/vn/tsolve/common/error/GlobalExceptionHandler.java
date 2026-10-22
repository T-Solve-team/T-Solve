package vn.tsolve.common.error;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

/**
 * Renders every error as RFC 9457 Problem Details with the SRS message code (`code`), its template values
 * (`params`) and, for invalid requests, one entry per field (`fieldErrors`) — the shape the web app reads.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Set<String> REQUIRED_CONSTRAINTS = Set.of("NotNull", "NotBlank", "NotEmpty");

    @ExceptionHandler(MessageCodeException.class)
    public ProblemDetail messageCode(MessageCodeException e) {
        return problem(e.getStatus(), e.getMessage(), e.getCode(), e.getParams());
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ProblemDetail uploadTooLarge(MaxUploadSizeExceededException e) {
        return problem(HttpStatus.PAYLOAD_TOO_LARGE, e.getMessage(), "MSG28", Map.of());
    }

    @ExceptionHandler(NotFoundException.class)
    public ProblemDetail notFound(NotFoundException e) {
        return problem(HttpStatus.NOT_FOUND, e.getMessage(), "MSG04", Map.of());
    }

    // no SRS code yet for these rules: the web app falls back to MSG06
    @ExceptionHandler(BusinessRuleException.class)
    public ProblemDetail rule(BusinessRuleException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, e.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail invalid(MethodArgumentNotValidException e) {
        List<Map<String, Object>> fieldErrors = e.getBindingResult().getFieldErrors().stream()
                .map(GlobalExceptionHandler::fieldError)
                .toList();
        String code =
                fieldErrors.isEmpty() ? "MSG06" : (String) fieldErrors.get(0).get("code");
        ProblemDetail p = problem(HttpStatus.BAD_REQUEST, "Validation failed", code, Map.of());
        p.setProperty("fieldErrors", fieldErrors);
        return p;
    }

    private static Map<String, Object> fieldError(FieldError f) {
        Map<String, Object> params = new HashMap<>();
        params.put("field_name", f.getField());
        String code = "MSG06";
        if (REQUIRED_CONSTRAINTS.contains(f.getCode())) {
            code = "MSG01";
        } else if ("Size".equals(f.getCode())) {
            code = "MSG02";
            // @Size arguments are [field name, max, min]
            Object[] args = f.getArguments();
            if (args != null && args.length > 1) {
                params.put("max_length", args[1]);
            }
        }
        return Map.of("field", f.getField(), "code", code, "params", Map.copyOf(params));
    }

    private static ProblemDetail problem(HttpStatus status, String detail, String code, Map<String, Object> params) {
        ProblemDetail p = ProblemDetail.forStatusAndDetail(status, detail);
        p.setProperty("code", code);
        p.setProperty("params", params);
        return p;
    }
}
