package vn.tsolve.ticket.exception;

import java.util.Map;
import org.springframework.http.HttpStatus;
import vn.tsolve.common.exception.FieldMessageCodeException;

/** A required Solution section is empty on Finish (MSG01 under that section, jira-app AC-002.5). */
public class SolutionSectionRequiredException extends FieldMessageCodeException {
    public SolutionSectionRequiredException(String fieldName, String field) {
        super(HttpStatus.BAD_REQUEST, "MSG01", Map.of("field_name", fieldName), field);
    }
}
