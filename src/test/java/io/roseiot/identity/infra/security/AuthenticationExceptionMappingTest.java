package io.roseiot.identity.infra.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class AuthenticationExceptionMappingTest {

    @Test
    void shouldMapBadCredentialsToInvalidCredentialsCode() {
        AuthenticationExceptionMapping.Mapped mapped =
                AuthenticationExceptionMapping.map(AuthenticationExceptionMapping.invalidCredentials());

        assertThat(mapped.httpStatus()).isEqualTo(401);
        assertThat(mapped.code()).isEqualTo("invalid_credentials");
        assertThat(mapped.message()).isEqualTo("invalid_credentials");
    }

    @Test
    void shouldMapInvalidRefreshTokenAuthenticationFailure() {
        AuthenticationExceptionMapping.Mapped mapped =
                AuthenticationExceptionMapping.map(AuthenticationExceptionMapping.invalidRefreshToken());

        assertThat(mapped.httpStatus()).isEqualTo(401);
        assertThat(mapped.code()).isEqualTo("invalid_refresh_token");
    }

    @Test
    void shouldMapUnknownAuthenticationFailuresToUnauthorized() {
        AuthenticationExceptionMapping.Mapped mapped =
                AuthenticationExceptionMapping.map(mock(AuthenticationException.class));

        assertThat(mapped.httpStatus()).isEqualTo(401);
        assertThat(mapped.code()).isEqualTo("unauthorized");
    }

    @Test
    void shouldNotEchoBadCredentialsDetailIntoTheResponseBody() {
        BadCredentialsException leaky = new BadCredentialsException("ops@roseiot.io / wrong password");

        AuthenticationExceptionMapping.Mapped mapped = AuthenticationExceptionMapping.map(leaky);

        assertThat(mapped.message()).isEqualTo("invalid_credentials");
    }
}
