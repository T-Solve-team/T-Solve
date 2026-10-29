package vn.tsolve.common.error;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    private static class SampleException extends MessageCodeException {
        SampleException() {
            super(HttpStatus.CONFLICT, "MSG40", Map.of("count", 3));
        }
    }

    @Test
    void messageCodeExceptionKeepsItsStatusCodeAndParams() {
        ProblemDetail p = handler.messageCode(new SampleException());

        assertThat(p.getStatus()).isEqualTo(409);
        assertThat(p.getProperties()).containsEntry("code", "MSG40").containsEntry("params", Map.of("count", 3));
    }

    @Test
    void uploadOverTheLimitIsMsg28_AC_001_4() {
        ProblemDetail p = handler.uploadTooLarge(new MaxUploadSizeExceededException(10L * 1024 * 1024));

        assertThat(p.getStatus()).isEqualTo(413);
        assertThat(p.getProperties()).containsEntry("code", "MSG28");
    }

    @Test
    void missingRoleIsForbiddenWithMsg08_AC_001_10() {
        ProblemDetail p = handler.accessDenied(new AccessDeniedException("Access Denied"));

        assertThat(p.getStatus()).isEqualTo(403);
        assertThat(p.getProperties()).containsEntry("code", "MSG08");
    }

    @Test
    void notFoundIsMsg04() {
        ProblemDetail p = handler.notFound(new NotFoundException("Solution 7 not found"));

        assertThat(p.getStatus()).isEqualTo(404);
        assertThat(p.getProperties()).containsEntry("code", "MSG04");
    }

    @Test
    void businessRuleIsConflictWithoutACode() {
        ProblemDetail p = handler.rule(new BusinessRuleException("Cannot move solution"));

        assertThat(p.getStatus()).isEqualTo(409);
        // Spring leaves properties null until one is set
        assertThat(p.getProperties()).isNull();
    }

    @Test
    void invalidRequestListsFieldErrorsWithMessageCodes() throws Exception {
        BeanPropertyBindingResult result = new BeanPropertyBindingResult(new Object(), "request");
        result.addError(new FieldError(
                "request", "rowNumbers", null, false, new String[] {"NotEmpty"}, null, "must not be empty"));
        result.addError(new FieldError(
                "request", "reason", "x", false, new String[] {"Size"}, new Object[] {null, 1000, 1}, "too long"));
        MethodParameter parameter =
                new MethodParameter(GlobalExceptionHandlerTest.class.getDeclaredMethod("sample", String.class), 0);

        ProblemDetail p = handler.invalid(new MethodArgumentNotValidException(parameter, result));

        assertThat(p.getStatus()).isEqualTo(400);
        assertThat(p.getProperties()).containsEntry("code", "MSG01");
        assertThat(p.getProperties().get("fieldErrors"))
                .isEqualTo(List.of(
                        Map.of("field", "rowNumbers", "code", "MSG01", "params", Map.of("field_name", "rowNumbers")),
                        Map.of(
                                "field",
                                "reason",
                                "code",
                                "MSG02",
                                "params",
                                Map.of("field_name", "reason", "max_length", 1000))));
    }

    @SuppressWarnings("unused")
    private void sample(String body) {}
}
