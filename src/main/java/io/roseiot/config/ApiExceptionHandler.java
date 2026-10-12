package io.roseiot.config;

import io.roseiot.shared.ApiErrorCode;
import io.roseiot.shared.BusinessException;
import io.roseiot.shared.ratelimit.RateLimitCapacityExceededException;
import io.roseiot.shared.ratelimit.RateLimitExceededException;
import io.roseiot.shared.webmvc.ApiHttpSupport;
import io.roseiot.shared.webmvc.RequestCorrelationFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpHeaders;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import org.springframework.web.util.WebUtils;

import java.net.URI;

@RestControllerAdvice(basePackages = "io.roseiot")
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    private final ApiHttpSupport apiHttpSupport;

    public ApiExceptionHandler(ApiHttpSupport apiHttpSupport) {
        this.apiHttpSupport = apiHttpSupport;
    }

    @ExceptionHandler(BusinessException.class)
    ResponseEntity<ProblemDetail> handleBusinessException(BusinessException exception) {
        log.error("Business exception requestId={}", MDC.get(RequestCorrelationFilter.MDC_KEY), exception);
        return apiHttpSupport.errorResponse(
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                exception.errorCode().code(),
                "An unexpected error occurred");
    }

    @ExceptionHandler(RateLimitExceededException.class)
    ResponseEntity<ProblemDetail> handleRateLimit(RateLimitExceededException exception) {
        String code = ApiErrorCode.RATE_LIMITED.code();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PROBLEM_JSON);
        headers.setCacheControl(CacheControl.noStore());
        headers.set("Retry-After", Long.toString(exception.retryAfterSeconds()));
        return new ResponseEntity<>(apiHttpSupport.problemDetail(
                HttpStatus.TOO_MANY_REQUESTS.value(), code, code), headers, HttpStatus.TOO_MANY_REQUESTS);
    }

    @ExceptionHandler(RateLimitCapacityExceededException.class)
    ResponseEntity<ProblemDetail> handleRateLimitCapacity(RateLimitCapacityExceededException exception) {
        return apiHttpSupport.errorResponse(HttpStatus.SERVICE_UNAVAILABLE.value(),
                ApiErrorCode.RATE_LIMIT_CAPACITY_EXHAUSTED.code(),
                "Rate limiting capacity is temporarily unavailable");
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException exception,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {
        ProblemDetail problem = frameworkProblem(status, "validation_error", "Validation failed", request);
        problem.setProperty("errors", exception.getBindingResult().getAllErrors().stream()
                .map(error -> java.util.Map.of(
                        "field", error instanceof FieldError fieldError
                                ? fieldError.getField()
                                : error.getObjectName(),
                        "message", error.getDefaultMessage() == null ? "Invalid value" : error.getDefaultMessage()))
                .toList());
        HttpHeaders responseHeaders = new HttpHeaders();
        responseHeaders.putAll(headers);
        responseHeaders.setContentType(MediaType.APPLICATION_PROBLEM_JSON);
        responseHeaders.setCacheControl(CacheControl.noStore());
        return new ResponseEntity<>(problem, responseHeaders, status);
    }

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(
            Exception exception,
            Object body,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {
        if (request instanceof ServletWebRequest servletRequest
                && servletRequest.getResponse() != null
                && servletRequest.getResponse().isCommitted()) {
            return null;
        }
        FrameworkProblem problem = frameworkProblem(exception, status);
        ProblemDetail detail = apiHttpSupport.problemDetail(status.value(), problem.code(), problem.detail());
        if (request instanceof ServletWebRequest servletRequest) {
            detail.setInstance(URI.create(servletRequest.getRequest().getRequestURI()));
        }
        HttpHeaders responseHeaders = new HttpHeaders();
        responseHeaders.putAll(headers);
        responseHeaders.setContentType(MediaType.APPLICATION_PROBLEM_JSON);
        responseHeaders.setCacheControl(CacheControl.noStore());
        if (status.is5xxServerError()) {
            request.setAttribute(WebUtils.ERROR_EXCEPTION_ATTRIBUTE, exception, WebRequest.SCOPE_REQUEST);
            log.error("Unhandled MVC exception requestId={}", MDC.get(RequestCorrelationFilter.MDC_KEY), exception);
        }
        return new ResponseEntity<>(detail, responseHeaders, status);
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ProblemDetail> handleUnexpected(Exception exception) {
        log.error("Unhandled API exception requestId={}", MDC.get(RequestCorrelationFilter.MDC_KEY), exception);
        return apiHttpSupport.errorResponse(
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                "internal_error",
                "An unexpected error occurred");
    }

    private ProblemDetail frameworkProblem(HttpStatusCode status, String code, String detail, WebRequest request) {
        ProblemDetail problem = apiHttpSupport.problemDetail(status.value(), code, detail);
        if (request instanceof ServletWebRequest servletRequest) {
            problem.setInstance(URI.create(servletRequest.getRequest().getRequestURI()));
        }
        return problem;
    }

    private static FrameworkProblem frameworkProblem(Exception exception, HttpStatusCode status) {
        if (exception instanceof MethodArgumentNotValidException) {
            return new FrameworkProblem("validation_error", "Validation failed");
        }
        if (exception instanceof org.springframework.http.converter.HttpMessageNotReadableException) {
            return new FrameworkProblem("bad_request", "Malformed request body");
        }
        if (exception instanceof org.springframework.web.HttpRequestMethodNotSupportedException) {
            return new FrameworkProblem("method_not_allowed", "HTTP method is not supported");
        }
        if (exception instanceof org.springframework.web.HttpMediaTypeNotSupportedException) {
            return new FrameworkProblem("unsupported_media_type", "Content type is not supported");
        }
        if (exception instanceof org.springframework.web.HttpMediaTypeNotAcceptableException) {
            return new FrameworkProblem("not_acceptable", "No acceptable response representation");
        }
        if (exception instanceof org.springframework.web.servlet.resource.NoResourceFoundException
                || exception instanceof org.springframework.web.servlet.NoHandlerFoundException) {
            return new FrameworkProblem("not_found", "Resource not found");
        }
        if (exception instanceof org.springframework.beans.TypeMismatchException
                || exception instanceof org.springframework.web.bind.MissingServletRequestParameterException) {
            return new FrameworkProblem("invalid_request", "Request parameters are invalid");
        }
        if (status.is5xxServerError()) {
            return new FrameworkProblem("internal_error", "An unexpected error occurred");
        }
        return new FrameworkProblem("invalid_request", "The request could not be processed");
    }

    private record FrameworkProblem(String code, String detail) {}
}
