package vn.tsolve.review.exception;

import java.util.Map;
import org.springframework.http.HttpStatus;
import vn.tsolve.common.exception.MessageCodeException;

/** AI check got no usable answer: the AI failed, timed out or broke the findings schema (AC-015.6). */
public class AiReviewUnavailableException extends MessageCodeException {

    public AiReviewUnavailableException() {
        super(HttpStatus.SERVICE_UNAVAILABLE, "MSG_NEW_AI_REVIEW_1", Map.of());
    }
}
