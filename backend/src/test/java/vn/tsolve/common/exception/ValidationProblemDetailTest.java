package vn.tsolve.common.exception;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.core.MethodParameter;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

class ValidationProblemDetailTest {
    @ParameterizedTest
    @ValueSource(strings = {"MSG01", "MSG02", "MSG06", "unknown"})
    void preservesValidatedCodeAndParams_BUG_039(String code) throws Exception {
        var binding = new BeanPropertyBindingResult(new Object(), "request");
        binding.addError(new FieldError("request", "reason", code));
        var detail = new GlobalExceptionHandler().invalid(error(binding));
        String expected = "unknown".equals(code) ? "MSG06" : code;
        Map<String, Object> params = "MSG02".equals(expected)
                ? Map.of("field_name", "Reason", "max_length", 1000)
                : Map.of("field_name", "Reason");
        assertThat(detail.getStatus()).isEqualTo(400);
        assertThat(detail.getProperties())
                .containsEntry("code", expected)
                .containsEntry("params", params)
                .containsEntry(
                        "fieldErrors", List.of(new GlobalExceptionHandler.ApiFieldError("reason", expected, params)));
    }

    @Test
    void fallsBackSafelyWhenNoFieldErrorsExist_BUG_039() throws Exception {
        var detail =
                new GlobalExceptionHandler().invalid(error(new BeanPropertyBindingResult(new Object(), "request")));
        assertThat(detail.getProperties()).containsEntry("code", "MSG06").containsEntry("fieldErrors", List.of());
    }

    private MethodArgumentNotValidException error(BeanPropertyBindingResult binding) throws Exception {
        return new MethodArgumentNotValidException(
                new MethodParameter(getClass().getDeclaredMethod("body", Object.class), 0), binding);
    }

    private void body(Object value) {}
}
