package vn.tsolve.ticket.exception;

import java.util.Map;
import org.springframework.http.HttpStatus;
import vn.tsolve.common.exception.MessageCodeException;

/** The uploaded file as a whole cannot be read as a ticket CSV (MSG27, MSG29, MSG04). */
public class CsvFileRejectedException extends MessageCodeException {

    public CsvFileRejectedException(String code) {
        this(code, Map.of());
    }

    public CsvFileRejectedException(String code, Map<String, Object> params) {
        super(HttpStatus.BAD_REQUEST, code, params);
    }
}
