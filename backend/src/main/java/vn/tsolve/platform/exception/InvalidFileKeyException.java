package vn.tsolve.platform.exception;

import java.util.Map;
import vn.tsolve.common.exception.NotFoundException;
import vn.tsolve.common.exception.ProblemDetails;

public class InvalidFileKeyException extends NotFoundException {

    public InvalidFileKeyException() {
        super(ProblemDetails.UNEXPECTED_ERROR, Map.of());
    }
}
