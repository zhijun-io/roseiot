package io.roseiot.config;

import io.roseiot.shared.webmvc.ApiHttpSupport;
import io.roseiot.shared.ApiErrorCode;
import io.roseiot.shared.BusinessException;
import io.roseiot.shared.ratelimit.RateLimitExceededException;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.ResponseEntity;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.validation.BindingResult;
import org.springframework.validation.MapBindingResult;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.context.request.ServletWebRequest;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

class ApiExceptionHandlerTest {

    private final ApiExceptionHandler cut =
            new ApiExceptionHandler(new ApiHttpSupport(JsonMapper.builder().build()));

    @Test
    void mapsBusinessExceptionsToInternalServerErrorWithoutExposingDetails() {
        assertBody(cut.handleBusinessException(
                        new BusinessException(ApiErrorCode.VALIDATION_ERROR, "private validation detail")),
                500, "validation_error", "An unexpected error occurred");
    }

    private void assertBody(ResponseEntity<ProblemDetail> response, int status, String code, String message) {
        assertThat(response.getStatusCode().value()).isEqualTo(status);
        assertThat(response.getBody())
                .isNotNull()
                .satisfies(problem -> {
                    assertThat(problem.getStatus()).isEqualTo(status);
                    assertThat(problem.getType().toString()).isEqualTo("urn:roseiot:problem:" + code);
                    assertThat(problem.getDetail()).isEqualTo(message);
                    assertThat(problem.getProperties()).containsEntry("code", code);
                });
    }

    private ResponseEntity<ProblemDetail> handleFrameworkException(Exception exception) throws Exception {
        MockHttpServletRequest servletRequest = new MockHttpServletRequest();
        servletRequest.setRequestURI("/test");
        ResponseEntity<Object> response = cut.handleException(exception, new ServletWebRequest(servletRequest));
        return new ResponseEntity<>((ProblemDetail) response.getBody(), response.getHeaders(), response.getStatusCode());
    }

    @Test
    void shouldAnswerRateLimitWithTooManyRequestsAndStableCode() {
        assertBody(cut.handleRateLimit(new RateLimitExceededException()), 429, "rate_limited", "rate_limited");
    }

    @Test
    void shouldAnswerBeanValidationFailureWithBadRequestAndViolationList() throws Exception {
        BindingResult bindingResult = new MapBindingResult(new java.util.HashMap<>(), "request");
        bindingResult.rejectValue("name", "NotBlank", "must not be blank");

        MethodParameter methodParameter = new MethodParameter(String.class.getMethod("length"), -1);
        ResponseEntity<ProblemDetail> response = handleFrameworkException(
                new MethodArgumentNotValidException(methodParameter, bindingResult));
        assertBody(response, 400, "validation_error", "Validation failed");
        assertThat(response.getBody().getProperties().get("errors"))
                .isEqualTo(java.util.List.of(java.util.Map.of("field", "name", "message", "must not be blank")));
        assertThat(response.getBody().getInstance()).hasToString("/test");
    }

    @Test
    void shouldAnswerUnreadableBodyWithBadRequest() throws Exception {
        HttpMessageNotReadableException exception =
                new HttpMessageNotReadableException("malformed json", (HttpInputMessage) null);

        assertBody(handleFrameworkException(exception), 400, "bad_request", "Malformed request body");
    }

    @Test
    void shouldAnswerParameterBindingFailureWithBadRequest() throws Exception {
        assertBody(handleFrameworkException(new MissingServletRequestParameterException("limit", "int")),
                400, "invalid_request", "Request parameters are invalid");
    }

    @Test
    void shouldAnswerUnexpectedFailureWithInternalErrorAndNoLeak() {
        assertBody(cut.handleUnexpected(new IllegalStateException("jdbc detail")),
                500, "internal_error", "An unexpected error occurred");
    }
}
