package vn.tsolve.common.exception;

import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;

/**
 * A request a service rejects for several fields at once (400): one {@code fieldErrors} entry per field. The first
 * error is also the top-level code, as for a rejected {@code @Valid} request.
 */
public class InvalidFieldsException extends MessageCodeException {

    private final List<ProblemDetails.FieldError> errors;

    public InvalidFieldsException(List<ProblemDetails.FieldError> errors) {
        super(
                HttpStatus.BAD_REQUEST,
                errors.get(0).code(),
                Map.copyOf(errors.get(0).params()));
        this.errors = List.copyOf(errors);
    }

    public List<ProblemDetails.FieldError> getErrors() {
        return errors;
    }
}
