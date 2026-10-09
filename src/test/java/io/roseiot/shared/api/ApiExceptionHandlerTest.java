package io.roseiot.shared.api;

import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.MethodArgumentNotValidException;
import io.roseiot.shared.ratelimit.RateLimitExceededException;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

class ApiExceptionHandlerTest {

    private final ApiExceptionHandler cut =
            new ApiExceptionHandler(new ApiErrorBodyWriter(JsonMapper.builder().build()));

    private void assertBody(ResponseEntity<ApiErrorResponse> response, int status, String code, String message) {
        assertThat(response.getStatusCode().value()).isEqualTo(status);
        assertThat(response.getBody())
                .isNotNull()
                .extracting(ApiErrorResponse::code, ApiErrorResponse::message)
                .containsExactly(code, message);
    }

    @Test
    void shouldAnswerRateLimitWithTooManyRequestsAndStableCode() {
        assertBody(cut.handleRateLimit(new RateLimitExceededException()),
                429, "rate_limited", "Too many requests");
    }

    @Test
    void shouldAnswerBeanValidationFailureWithBadRequestWithoutFieldDetails() throws NoSuchMethodException {
        BindingResult bindingResult = new BeanPropertyBindingResult(new Object(), "request");

        MethodParameter methodParameter = new MethodParameter(String.class.getMethod("length"), -1);
        assertBody(cut.handleValidation(new MethodArgumentNotValidException(methodParameter, bindingResult)),
                400, "validation_error", "Validation failed");
    }

    @Test
    void shouldAnswerUnreadableBodyWithBadRequest() {
        HttpMessageNotReadableException exception =
                new HttpMessageNotReadableException("malformed json", (HttpInputMessage) null);

        assertBody(cut.handleUnreadable(exception), 400, "bad_request", "Malformed request body");
    }

    @Test
    void shouldAnswerUnexpectedFailureWithInternalErrorAndNoLeak() {
        assertBody(cut.handleUnexpected(new IllegalStateException("jdbc detail")),
                500, "internal_error", "An unexpected error occurred");
    }
}
