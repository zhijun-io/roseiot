package io.roseiot.identity.web;

import io.roseiot.identity.domain.InvalidCredentialsException;
import io.roseiot.identity.domain.InvalidRecoveryTokenException;
import io.roseiot.identity.domain.InvalidRefreshTokenException;
import io.roseiot.identity.domain.PasswordValidationException;
import io.roseiot.shared.api.ApiErrorBodyWriter;
import io.roseiot.shared.api.ApiErrorResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

class IdentityExceptionHandlerTest {

    private final IdentityExceptionHandler cut =
            new IdentityExceptionHandler(new ApiErrorBodyWriter(JsonMapper.builder().build()));

    private void assertBody(ResponseEntity<ApiErrorResponse> response, int status, String code, String message) {
        assertThat(response.getStatusCode().value()).isEqualTo(status);
        assertThat(response.getBody())
                .isNotNull()
                .extracting(ApiErrorResponse::code, ApiErrorResponse::message)
                .containsExactly(code, message);
    }

    @Test
    void shouldAnswerBadCredentialsWithUnauthorizedAndOneStableCode() {
        assertBody(cut.handleInvalidCredentials(new InvalidCredentialsException()),
                401, "invalid_credentials", "Invalid email or password");
    }

    @Test
    void shouldAnswerRefreshTokenFailureWithItsOwnCode() {
        assertBody(cut.handleInvalidRefreshToken(new InvalidRefreshTokenException()),
                401, "invalid_refresh_token", "Invalid refresh token");
    }

    @Test
    void shouldAnswerRecoveryTokenFailureWithItsOwnCode() {
        assertBody(cut.handleInvalidRecoveryToken(new InvalidRecoveryTokenException()),
                401, "invalid_recovery_token", "Invalid recovery token");
    }

    @Test
    void shouldAnswerRejectedPasswordWithBadRequestAndTheRuleThatFailed() {
        assertBody(cut.handlePasswordValidation(
                        new PasswordValidationException("Password must contain letters and digits")),
                400, "validation_error", "Password must contain letters and digits");
    }

    @Test
    void shouldAnswerAccessDeniedWithForbidden() {
        assertBody(cut.handleAccessDenied(new AccessDeniedException("denied")),
                403, "forbidden", "Forbidden");
    }

    @Test
    void shouldNotEchoExceptionDetailsIntoTheStableErrorBody() {
        InvalidRefreshTokenException leaky = new InvalidRefreshTokenException() {
            @Override
            public String getMessage() {
                return "session 01a1 not found for user ops@roseiot.io";
            }
        };

        // The handler maps by exception type and writes its own message, so internals cannot escape.
        assertBody(cut.handleInvalidRefreshToken(leaky), 401, "invalid_refresh_token", "Invalid refresh token");
    }
}
