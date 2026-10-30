package vn.tsolve.common.exception;

import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toList;

import jakarta.validation.ConstraintViolation;
import java.lang.reflect.Array;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationTrustResolver;
import org.springframework.security.authentication.AuthenticationTrustResolverImpl;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Renders every error as a {@link ProblemDetail} with the SRS message code. Spring MVC's own errors keep Spring's
 * status and body and get {@code code} MSG06.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private static final String REQUIRED = "MSG01";
    private static final String TOO_LONG = "MSG02";
    private static final String INVALID_EMAIL = "MSG03";
    private static final String NO_RESULTS = "MSG04";
    private static final String NO_PERMISSION = "MSG08";
    private static final String UPLOAD_TOO_LARGE = "MSG28";

    /** When one field fails several rules, the earliest code here is the one shown. */
    private static final List<String> FIELD_CODE_PRECEDENCE =
            List.of(REQUIRED, TOO_LONG, INVALID_EMAIL, ProblemDetails.UNEXPECTED_ERROR);

    private static final Comparator<ProblemDetails.FieldError> BY_PRECEDENCE =
            Comparator.comparingInt(error -> FIELD_CODE_PRECEDENCE.indexOf(error.code()));

    private static final AuthenticationTrustResolver TRUST_RESOLVER = new AuthenticationTrustResolverImpl();

    @ExceptionHandler(NotFoundException.class)
    ProblemDetail handleNotFound(NotFoundException e) {
        ProblemDetail problem = coded(HttpStatus.NOT_FOUND, e);
        return e.getCode() == null ? ProblemDetails.withCode(problem, NO_RESULTS, Map.of()) : problem;
    }

    @ExceptionHandler(MessageCodeException.class)
    ProblemDetail handleMessageCode(MessageCodeException e) {
        return coded(e.getStatus(), e);
    }

    @ExceptionHandler(BusinessRuleException.class)
    ProblemDetail handleBusinessRule(BusinessRuleException e) {
        return coded(HttpStatus.CONFLICT, e);
    }

    // Method security throws inside the DispatcherServlet, before Spring Security's own handler could see it,
    // so without this the catch-all below would turn a denial into a 500. An anonymous user's denial is rethrown:
    // ExceptionTranslationFilter sends it to the entry point (401 MSG07), as it would without this advice.
    @ExceptionHandler(AccessDeniedException.class)
    ProblemDetail handleAccessDenied(AccessDeniedException e) {
        if (TRUST_RESOLVER.isAnonymous(SecurityContextHolder.getContext().getAuthentication())) {
            throw e;
        }
        return ProblemDetails.of(HttpStatus.FORBIDDEN, NO_PERMISSION, Map.of());
    }

    // Rethrown so the catch-all does not make it a 500: ExceptionTranslationFilter answers it through the entry point.
    @ExceptionHandler(AuthenticationException.class)
    void rethrowAuthentication(AuthenticationException e) {
        throw e;
    }

    // No detail: the exception's message may hold personal data or internals; it goes to the log only.
    @ExceptionHandler(Exception.class)
    ProblemDetail handleUnexpected(Exception e) {
        log.error("Unhandled exception", e);
        return ProblemDetails.of(HttpStatus.INTERNAL_SERVER_ERROR, ProblemDetails.UNEXPECTED_ERROR, Map.of());
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        List<ProblemDetails.FieldError> fieldErrors = ex.getBindingResult().getFieldErrors().stream()
                .collect(groupingBy(FieldError::getField, TreeMap::new, toList()))
                .entrySet()
                .stream()
                .map(entry -> shownError(entry.getKey(), entry.getValue()))
                .toList();
        ProblemDetail body = ex.getBody();
        // The list stays sorted by field name; the top-level code is the most important one, not the first.
        fieldErrors.stream()
                .min(BY_PRECEDENCE)
                .ifPresentOrElse(
                        shown -> ProblemDetails.withCode(body, shown.code(), shown.params()),
                        () -> ProblemDetails.withCode(body, ProblemDetails.UNEXPECTED_ERROR, Map.of()));
        ProblemDetails.withFieldErrors(body, fieldErrors);
        return handleExceptionInternal(ex, body, headers, status, request);
    }

    // The servlet's multipart limit rejects the file before any controller can check its size.
    @Override
    protected ResponseEntity<Object> handleMaxUploadSizeExceededException(
            MaxUploadSizeExceededException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        ProblemDetail body = ProblemDetails.withCode(ex.getBody(), UPLOAD_TOO_LARGE, Map.of());
        return handleExceptionInternal(ex, body, headers, status, request);
    }

    @Override
    protected ResponseEntity<Object> createResponseEntity(
            Object body, HttpHeaders headers, HttpStatusCode statusCode, WebRequest request) {
        if (body instanceof ProblemDetail problem && !ProblemDetails.hasCode(problem)) {
            ProblemDetails.withCode(problem, ProblemDetails.UNEXPECTED_ERROR, Map.of());
        }
        return super.createResponseEntity(body, headers, statusCode, request);
    }

    // A message-only exception still gets a code (MSG06) so every error body has one; its message stays in detail.
    private static ProblemDetail coded(HttpStatus status, CodedException e) {
        if (e.getCode() == null) {
            return ProblemDetails.withCode(
                    ProblemDetail.forStatusAndDetail(status, e.getMessage()),
                    ProblemDetails.UNEXPECTED_ERROR,
                    Map.of());
        }
        ProblemDetail problem = ProblemDetails.of(status, e.getCode(), e.getParams());
        if (e.getField() != null) {
            ProblemDetails.withFieldErrors(
                    problem, List.of(new ProblemDetails.FieldError(e.getField(), e.getCode(), e.getParams())));
        }
        return problem;
    }

    private static ProblemDetails.FieldError shownError(String field, List<FieldError> errors) {
        return errors.stream()
                .map(error -> toFieldError(field, error))
                .min(BY_PRECEDENCE)
                .orElseThrow();
    }

    // The constraint's simple name is the error code; a binding failure ("typeMismatch") is not a constraint.
    private static ProblemDetails.FieldError toFieldError(String field, FieldError error) {
        String constraint = error.getCode() == null ? "" : error.getCode();
        return switch (constraint) {
            case "NotBlank", "NotNull", "NotEmpty" -> new ProblemDetails.FieldError(
                    field, REQUIRED, Map.of("field_name", field));
                // A Spring Validator may reject with "Size" too; only Bean Validation says what max is.
            case "Size" -> {
                if (!error.contains(ConstraintViolation.class)) {
                    yield new ProblemDetails.FieldError(field, ProblemDetails.UNEXPECTED_ERROR, Map.of());
                }
                int max = (int) error.unwrap(ConstraintViolation.class)
                        .getConstraintDescriptor()
                        .getAttributes()
                        .get("max");
                // Only "longer than max" is MSG02; a value under @Size(min) is another rule.
                yield lengthOf(error.getRejectedValue()) > max
                        ? new ProblemDetails.FieldError(field, TOO_LONG, Map.of("field_name", field, "max_length", max))
                        : new ProblemDetails.FieldError(field, ProblemDetails.UNEXPECTED_ERROR, Map.of());
            }
            case "Email" -> new ProblemDetails.FieldError(field, INVALID_EMAIL, Map.of());
            default -> new ProblemDetails.FieldError(field, ProblemDetails.UNEXPECTED_ERROR, Map.of());
        };
    }

    private static int lengthOf(Object value) {
        if (value instanceof CharSequence text) {
            return text.length();
        }
        if (value instanceof Collection<?> items) {
            return items.size();
        }
        if (value != null && value.getClass().isArray()) {
            return Array.getLength(value);
        }
        return value instanceof Map<?, ?> entries ? entries.size() : 0;
    }
}
