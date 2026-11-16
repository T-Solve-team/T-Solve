package vn.tsolve.wiki.exception;

import java.util.Map;
import org.springframework.http.HttpStatus;
import vn.tsolve.common.exception.MessageCodeException;

public class WikiFolderInvalidParentException extends MessageCodeException {
    public WikiFolderInvalidParentException() {
        super(HttpStatus.BAD_REQUEST, "MSG86", Map.of(), "parentId");
    }
}
