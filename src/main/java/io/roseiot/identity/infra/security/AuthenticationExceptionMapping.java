package io.roseiot.identity.infra.security;

import io.roseiot.shared.domain.ApiErrorCode;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;

/** Stable JSON {@code code} / {@code message} for Spring Security {@link AuthenticationException} types. */
public final class AuthenticationExceptionMapping {

    private AuthenticationExceptionMapping() {}

    public record Mapped(int httpStatus, String code, String message) {}

    public static Mapped map(AuthenticationException exception) {
        if (exception instanceof InvalidRefreshTokenAuthenticationException) {
            String code = ApiErrorCode.INVALID_REFRESH_TOKEN.code();
            return new Mapped(HttpStatus.UNAUTHORIZED.value(), code, code);
        }
        if (exception instanceof BadCredentialsException) {
            String code = ApiErrorCode.INVALID_CREDENTIALS.code();
            return new Mapped(HttpStatus.UNAUTHORIZED.value(), code, code);
        }
        return new Mapped(HttpStatus.UNAUTHORIZED.value(), "unauthorized", "unauthorized");
    }

    public static BadCredentialsException invalidCredentials() {
        return new BadCredentialsException(ApiErrorCode.INVALID_CREDENTIALS.code());
    }

    public static InvalidRefreshTokenAuthenticationException invalidRefreshToken() {
        return new InvalidRefreshTokenAuthenticationException();
    }
}
