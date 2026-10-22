package vn.tsolve.common.exception;

import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger LOG = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    public ProblemDetail application(ApiException error) {
        ProblemDetail detail = problem(error.getStatus(), error.getCode());
        detail.setProperty("params", error.getParams());
        if (error.getField() != null) {
            detail.setProperty(
                    "fieldErrors", List.of(new ApiFieldError(error.getField(), error.getCode(), error.getParams())));
        }
        return detail;
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail invalid(MethodArgumentNotValidException error) {
        ProblemDetail detail = problem(HttpStatus.BAD_REQUEST, "MSG01");
        detail.setProperty(
                "fieldErrors",
                error.getBindingResult().getFieldErrors().stream()
                        .map(this::fieldError)
                        .toList());
        return detail;
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    public ProblemDetail malformed(Exception error) {
        return problem(HttpStatus.BAD_REQUEST, "MSG06");
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ProblemDetail forbidden(AccessDeniedException error) {
        return problem(HttpStatus.FORBIDDEN, "MSG08");
    }

    @ExceptionHandler(ConcurrencyFailureException.class)
    public ProblemDetail concurrent(ConcurrencyFailureException error) {
        return problem(HttpStatus.CONFLICT, "MSG06");
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail failed(Exception error) {
        if (error instanceof ErrorResponse response) {
            ProblemDetail detail = ProblemDetail.forStatus(response.getStatusCode());
            detail.setProperty("code", "MSG06");
            return detail;
        }
        // Do not log exception payloads: database errors can contain ticket text or identity data.
        LOG.error("Request failed ({})", error.getClass().getSimpleName());
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "MSG06");
    }

    private ApiFieldError fieldError(FieldError error) {
        String code = error.getDefaultMessage();
        if (!List.of("MSG01", "MSG02", "MSG06").contains(code)) code = "MSG06";
        String label =
                switch (error.getField()) {
                    case "reason" -> "Reason";
                    case "comment" -> "Comment";
                    default -> "Ticket";
                };
        Map<String, Object> params =
                "MSG02".equals(code) ? Map.of("field_name", label, "max_length", 1000) : Map.of("field_name", label);
        return new ApiFieldError(error.getField(), code, params);
    }

    private ProblemDetail problem(HttpStatus status, String code) {
        ProblemDetail detail = ProblemDetail.forStatus(status);
        detail.setProperty("code", code);
        return detail;
    }

    public record ApiFieldError(String field, String code, Map<String, Object> params) {}
}
