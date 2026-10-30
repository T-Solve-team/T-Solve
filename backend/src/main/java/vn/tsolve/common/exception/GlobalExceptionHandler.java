package vn.tsolve.common.exception;

import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.minBy;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Path;
import jakarta.validation.metadata.ConstraintDescriptor;
import java.lang.reflect.Array;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.MessageSourceResolvable;
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
import org.springframework.validation.method.ParameterErrors;
import org.springframework.validation.method.ParameterValidationResult;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
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

    // The exception's message may hold personal data (backend/AGENTS.md §9): the body has no detail and the ERROR
    // line names only the class; the full exception is logged at DEBUG. The instance id ties a report to the log.
    @ExceptionHandler(Exception.class)
    ProblemDetail handleUnexpected(Exception e) {
        String id = UUID.randomUUID().toString();
        log.error("Unhandled exception {} id={}", e.getClass().getName(), id);
        log.debug("Unhandled exception id={}", id, e);
        ProblemDetail problem =
                ProblemDetails.of(HttpStatus.INTERNAL_SERVER_ERROR, ProblemDetails.UNEXPECTED_ERROR, Map.of());
        problem.setInstance(URI.create("urn:uuid:" + id));
        return problem;
    }

    // A @Validated service (or Hibernate at flush) rejects a value: the same 400 as a rejected request.
    @ExceptionHandler(ConstraintViolationException.class)
    ProblemDetail handleConstraintViolation(ConstraintViolationException e) {
        Set<ConstraintViolation<?>> violations =
                e.getConstraintViolations() == null ? Set.of() : e.getConstraintViolations();
        List<ProblemDetails.FieldError> errors = violations.stream()
                .map(violation -> fromViolation(lastNode(violation.getPropertyPath()), violation))
                .toList();
        return withValidationErrors(ProblemDetail.forStatus(HttpStatus.BAD_REQUEST), errors);
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        List<ProblemDetails.FieldError> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> toFieldError(error.getField(), error))
                .toList();
        return handleExceptionInternal(ex, withValidationErrors(ex.getBody(), errors), headers, status, request);
    }

    // Constraints on @RequestParam / @PathVariable (Spring's built-in method validation); a @Valid argument among
    // them comes as ParameterErrors with its own field errors.
    @Override
    protected ResponseEntity<Object> handleHandlerMethodValidationException(
            HandlerMethodValidationException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        List<ProblemDetails.FieldError> errors = new ArrayList<>();
        for (ParameterValidationResult result : ex.getAllValidationResults()) {
            if (result instanceof ParameterErrors beanErrors) {
                beanErrors.getFieldErrors().forEach(error -> errors.add(toFieldError(error.getField(), error)));
            } else {
                String field = Objects.toString(result.getMethodParameter().getParameterName(), "");
                result.getResolvableErrors()
                        .forEach(error -> errors.add(toFieldError(field, error, result.getArgument())));
            }
        }
        return handleExceptionInternal(ex, withValidationErrors(ex.getBody(), errors), headers, status, request);
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

    // One entry per field, sorted by field name. On a field that fails several rules, and for the top-level code and
    // params, the code earliest in FIELD_CODE_PRECEDENCE wins.
    private static ProblemDetail withValidationErrors(ProblemDetail body, List<ProblemDetails.FieldError> errors) {
        List<ProblemDetails.FieldError> shown = errors.stream()
                .collect(groupingBy(ProblemDetails.FieldError::field, TreeMap::new, minBy(BY_PRECEDENCE)))
                .values()
                .stream()
                .map(Optional::orElseThrow)
                .toList();
        shown.stream()
                .min(BY_PRECEDENCE)
                .ifPresentOrElse(
                        top -> ProblemDetails.withCode(body, top.code(), top.params()),
                        () -> ProblemDetails.withCode(body, ProblemDetails.UNEXPECTED_ERROR, Map.of()));
        return ProblemDetails.withFieldErrors(body, shown);
    }

    // Bean Validation behind the error gives the constraint and its attributes; a Spring Validator or a binding
    // failure ("typeMismatch") gives only a code.
    private static ProblemDetails.FieldError toFieldError(String field, FieldError error) {
        return error.contains(ConstraintViolation.class)
                ? fromViolation(field, error.unwrap(ConstraintViolation.class))
                : fromConstraint(field, error.getCode(), null, error.getRejectedValue());
    }

    // Spring's method validation names the constraint in the last code and, for @Size, passes the arguments
    // [field name, max, min] (attributes sorted by name).
    private static ProblemDetails.FieldError toFieldError(String field, MessageSourceResolvable error, Object value) {
        String[] codes = error.getCodes();
        String constraint = codes == null || codes.length == 0 ? null : codes[codes.length - 1];
        Object[] arguments = error.getArguments();
        Integer max = "Size".equals(constraint) && arguments != null && arguments.length > 1
                ? (arguments[1] instanceof Integer limit ? limit : null)
                : null;
        return fromConstraint(field, constraint, max, value);
    }

    private static ProblemDetails.FieldError fromViolation(String field, ConstraintViolation<?> violation) {
        ConstraintDescriptor<?> descriptor = violation.getConstraintDescriptor();
        Object max = descriptor.getAttributes().get("max");
        return fromConstraint(
                field,
                descriptor.getAnnotation().annotationType().getSimpleName(),
                max instanceof Integer limit ? limit : null,
                violation.getInvalidValue());
    }

    private static ProblemDetails.FieldError fromConstraint(
            String field, String constraint, Integer max, Object value) {
        return switch (constraint == null ? "" : constraint) {
            case "NotBlank", "NotNull", "NotEmpty" -> new ProblemDetails.FieldError(
                    field, REQUIRED, Map.of("field_name", field));
                // Only "longer than max" is MSG02; a value under @Size(min), or a "Size" with no max known, is another
                // rule.
            case "Size" -> max != null && lengthOf(value) > max
                    ? new ProblemDetails.FieldError(field, TOO_LONG, Map.of("field_name", field, "max_length", max))
                    : new ProblemDetails.FieldError(field, ProblemDetails.UNEXPECTED_ERROR, Map.of());
            case "Email" -> new ProblemDetails.FieldError(field, INVALID_EMAIL, Map.of());
            default -> new ProblemDetails.FieldError(field, ProblemDetails.UNEXPECTED_ERROR, Map.of());
        };
    }

    // The field of a service's violation: the last named node of its path ("create.arg0.title" → "title").
    private static String lastNode(Path path) {
        String name = null;
        for (Path.Node node : path) {
            if (node.getName() != null) {
                name = node.getName();
            }
        }
        return name == null ? path.toString() : name;
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
