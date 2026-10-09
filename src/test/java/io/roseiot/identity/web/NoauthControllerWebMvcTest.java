package io.roseiot.identity.web;

import io.roseiot.identity.application.PasswordResetService;
import io.roseiot.identity.application.PasswordResetService.ResetTokenState;
import io.roseiot.identity.application.UserSessionService;
import io.roseiot.identity.domain.InvalidResetTokenException;
import io.roseiot.identity.infra.security.IdentityJwtAuthenticationConverter;
import io.roseiot.identity.infra.security.JsonSecurityHandlers;
import io.roseiot.identity.infra.security.SecurityConfig;
import io.roseiot.shared.api.ApiErrorBodyWriter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@WebMvcTest(controllers = NoauthController.class)
@TestPropertySource(properties = "roseiot.rate-limit.enabled=false")
@Import({SecurityConfig.class, JsonSecurityHandlers.class, ApiErrorBodyWriter.class})
class NoauthControllerWebMvcTest {

    @Autowired
    private MockMvcTester mvc;

    @MockitoBean
    private PasswordResetService passwordResetService;

    @MockitoBean
    private UserSessionService sessionService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @MockitoBean
    private IdentityJwtAuthenticationConverter jwtAuthenticationConverter;

    @Test
    void shouldAcceptResetPasswordByEmailWithOkAndNoBody() {
        assertThat(mvc.post().uri("/api/noauth/password-reset/email")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"email": "nobody@roseiot.io"}
                        """))
                .hasStatus(HttpStatus.OK);

        verify(passwordResetService).requestResetByEmail("nobody@roseiot.io");
    }

    @Test
    void shouldRejectResetPasswordByEmailWithBlankEmail() {
        assertThat(mvc.post().uri("/api/noauth/password-reset/email")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"email": ""}
                        """))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson()
                .extractingPath("$.code")
                .isEqualTo("validation_error");

        verifyNoInteractions(passwordResetService);
    }

    @Test
    void shouldRedirectValidResetTokenToThePasswordForm() {
        given(passwordResetService.checkResetToken("plain-token")).willReturn(ResetTokenState.VALID);

        assertThat(mvc.get().uri("/api/noauth/password-reset?resetToken=plain-token"))
                .hasStatus(HttpStatus.SEE_OTHER)
                .headers()
                .hasValue("Location", "/auth/password-reset?resetToken=plain-token");
    }

    @Test
    void shouldAnswerInvalidResetTokenWithConflict() {
        given(passwordResetService.checkResetToken("bad")).willReturn(ResetTokenState.INVALID);

        assertThat(mvc.get().uri("/api/noauth/password-reset?resetToken=bad"))
                .hasStatus(HttpStatus.CONFLICT);
    }

    @Test
    void shouldRedirectExpiredResetTokenToTheExpiredPage() {
        given(passwordResetService.checkResetToken("old")).willReturn(ResetTokenState.EXPIRED);

        assertThat(mvc.get().uri("/api/noauth/password-reset?resetToken=old"))
                .hasStatus(HttpStatus.SEE_OTHER)
                .headers()
                .hasValue("Location", "/auth/password-reset/expired");
    }

    @Test
    void shouldResetPasswordWithTbShapedBody() {
        assertThat(mvc.post().uri("/api/noauth/password-reset")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"resetToken": "reset-token", "password": "Passw0rd!"}
                        """))
                .hasStatus(HttpStatus.NO_CONTENT);

        verify(passwordResetService).confirmPasswordReset("reset-token", "Passw0rd!");
    }

    @Test
    void shouldAnswerInvalidResetTokenOnPostWithBadRequest() {
        willThrow(new InvalidResetTokenException()).given(passwordResetService).confirmPasswordReset(any(), any());

        assertThat(mvc.post().uri("/api/noauth/password-reset")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"resetToken": "used-token", "password": "Passw0rd!"}
                        """))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson()
                .extractingPath("$.code")
                .isEqualTo("invalid_reset_token");
    }

    @Test
    void shouldAnswerActivateEndpointsWithNotImplemented() {
        assertThat(mvc.get().uri("/api/noauth/user-activation?activateToken=t"))
                .hasStatus(HttpStatus.NOT_IMPLEMENTED)
                .bodyJson()
                .extractingPath("$.code")
                .isEqualTo("not_implemented");

        assertThat(mvc.post().uri("/api/noauth/user-activation")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"activateToken": "t", "password": "Passw0rd!"}
                        """))
                .hasStatus(HttpStatus.NOT_IMPLEMENTED);
    }
}
