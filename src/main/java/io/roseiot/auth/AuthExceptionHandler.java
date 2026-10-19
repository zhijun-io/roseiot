package io.roseiot.auth;

import java.net.URI;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
class AuthExceptionHandler {
    private static final URI AUTH_FAILURE = URI.create("https://errors.roseiot.io/authentication-failed");
    private static final URI RESET_FAILURE = URI.create("https://errors.roseiot.io/password-reset-invalid");
    private static final URI PASSWORD_POLICY = URI.create("https://errors.roseiot.io/password-policy");

    @ExceptionHandler(InvalidCredentialsException.class)
    ProblemDetail invalidCredentials() {
        var problem = ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, "Invalid email or password.");
        problem.setType(AUTH_FAILURE);
        problem.setTitle("Authentication Failed");
        return problem;
    }

    @ExceptionHandler(InvalidResetTokenException.class)
    ProblemDetail invalidResetToken() {
        var problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.UNPROCESSABLE_ENTITY, "The password reset link is invalid or expired.");
        problem.setType(RESET_FAILURE);
        problem.setTitle("Password Reset Failed");
        return problem;
    }

    @ExceptionHandler(InvalidPasswordException.class)
    ProblemDetail invalidPassword(InvalidPasswordException exception) {
        var problem = ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_ENTITY, exception.getMessage());
        problem.setType(PASSWORD_POLICY);
        problem.setTitle("Password Does Not Meet Policy");
        return problem;
    }

    @ExceptionHandler(RateLimitedException.class)
    ResponseEntity<ProblemDetail> rateLimited() {
        var problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.TOO_MANY_REQUESTS, "Too many authentication attempts. Try again later.");
        problem.setType(URI.create("https://errors.roseiot.io/authentication-rate-limited"));
        problem.setTitle("Too Many Requests");
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header("Retry-After", "900")
                .body(problem);
    }
}
