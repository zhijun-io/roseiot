package io.roseiot.identity.infra.security;

import io.roseiot.shared.domain.ApiErrorCode;
import org.springframework.security.core.AuthenticationException;

/** Refresh token rejected; mapped to {@link ApiErrorCode#INVALID_REFRESH_TOKEN} at the API boundary. */
public class InvalidRefreshTokenAuthenticationException extends AuthenticationException {

    public InvalidRefreshTokenAuthenticationException() {
        super(ApiErrorCode.INVALID_REFRESH_TOKEN.code());
    }
}
