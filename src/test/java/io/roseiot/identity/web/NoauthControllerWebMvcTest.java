package io.roseiot.identity.web;

import io.roseiot.identity.application.PasswordResetService;
import io.roseiot.identity.application.PasswordResetService.ResetTokenState;
import io.roseiot.identity.application.UserSessionService;
import io.roseiot.tenant.application.TenantInvitationService;
import io.roseiot.tenant.application.TenantInvitationService.InvitationTokenState;
import io.roseiot.shared.domain.ApiErrorCode;
import io.roseiot.shared.domain.BusinessException;
import io.roseiot.identity.infra.security.IdentityJwtAuthenticationConverter;
import io.roseiot.identity.infra.security.ImportRoseiotSecuredWebMvc;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
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
@ImportRoseiotSecuredWebMvc
class NoauthControllerWebMvcTest {

    @Autowired
    private MockMvcTester mvc;

    @MockitoBean
    private PasswordResetService passwordResetService;

    @MockitoBean
    private TenantInvitationService tenantInvitationService;

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
    void shouldAnswerValidResetTokenWithOkAndStatus() {
        given(passwordResetService.checkResetToken("plain-token")).willReturn(ResetTokenState.VALID);

        assertThat(mvc.get().uri("/api/noauth/password-reset?resetToken=plain-token"))
                .hasStatus(HttpStatus.OK)
                .bodyJson()
                .extractingPath("$.status")
                .isEqualTo("valid");
    }

    @Test
    void shouldAnswerValidResetTokenEvenWhenAuthorizationHeaderIsInvalid() {
        given(passwordResetService.checkResetToken("plain-token")).willReturn(ResetTokenState.VALID);

        assertThat(mvc.get()
                        .uri("/api/noauth/password-reset?resetToken=plain-token")
                        .header("Authorization", "Bearer not-a-valid-jwt"))
                .hasStatus(HttpStatus.OK)
                .bodyJson()
                .extractingPath("$.status")
                .isEqualTo("valid");
    }

    @Test
    void shouldAnswerInvalidResetTokenWithStatusInvalid() {
        given(passwordResetService.checkResetToken("bad")).willReturn(ResetTokenState.INVALID);

        assertThat(mvc.get().uri("/api/noauth/password-reset?resetToken=bad"))
                .hasStatus(HttpStatus.OK)
                .bodyJson()
                .extractingPath("$.status")
                .isEqualTo("invalid");
    }

    @Test
    void shouldAnswerExpiredResetTokenWithStatusExpired() {
        given(passwordResetService.checkResetToken("old")).willReturn(ResetTokenState.EXPIRED);

        assertThat(mvc.get().uri("/api/noauth/password-reset?resetToken=old"))
                .hasStatus(HttpStatus.OK)
                .bodyJson()
                .extractingPath("$.status")
                .isEqualTo("expired");
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
        willThrow(new BusinessException(ApiErrorCode.INVALID_RESET_TOKEN))
                .given(passwordResetService)
                .confirmPasswordReset(any(), any());

        assertThat(mvc.post().uri("/api/noauth/password-reset")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"resetToken": "used-token", "password": "Passw0rd!"}
                        """))
                .hasStatus(HttpStatus.INTERNAL_SERVER_ERROR)
                .bodyJson()
                .extractingPath("$.code")
                .isEqualTo("invalid_reset_token");
    }

    @Test
    void shouldAnswerValidInvitationTokenWithStatus() {
        given(tenantInvitationService.checkInvitationToken("plain-token"))
                .willReturn(InvitationTokenState.VALID);

        assertThat(mvc.get().uri("/api/noauth/tenant-invitations?invitationToken=plain-token"))
                .hasStatus(HttpStatus.OK)
                .bodyJson()
                .extractingPath("$.status")
                .isEqualTo("valid");
    }

    @Test
    void shouldAcceptInvitationAndReturnSessionTokens() {
        given(tenantInvitationService.acceptInvitation("invite-token", "Passw0rd!"))
                .willReturn(new UserSessionService.SessionTokens("access-token", "refresh-token"));

        assertThat(mvc.post().uri("/api/noauth/tenant-invitations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"invitationToken": "invite-token", "password": "Passw0rd!"}
                                """))
                .hasStatus(HttpStatus.CREATED)
                .bodyJson()
                .extractingPath("$.accessToken")
                .isEqualTo("access-token");
    }

    @Test
    void shouldAnswerActivateEndpointsWithNotImplemented() {
        assertThat(mvc.get().uri("/api/noauth/user-activation?activateToken=t"))
                .hasStatus(HttpStatus.INTERNAL_SERVER_ERROR)
                .bodyJson()
                .extractingPath("$.code")
                .isEqualTo("not_implemented");

        assertThat(mvc.post().uri("/api/noauth/user-activation")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"activateToken": "t", "password": "Passw0rd!"}
                        """))
                .hasStatus(HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
