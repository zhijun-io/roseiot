package io.roseiot.iam.web;

import io.roseiot.iam.application.UserCredentialService;
import io.roseiot.iam.application.UserService;
import io.roseiot.iam.application.UserCredentialService.ResetTokenState;
import io.roseiot.shared.domain.ApiErrorCode;
import io.roseiot.shared.domain.BusinessException;
import io.roseiot.iam.infra.security.IdentityJwtAuthenticationConverter;
import io.roseiot.iam.infra.security.ImportRoseiotSecuredWebMvc;
import io.roseiot.iam.infra.security.SecurityUser;
import io.roseiot.iam.persistence.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;

@WebMvcTest(controllers = {UserController.class, UserCredentialController.class})
@TestPropertySource(properties = "roseiot.rate-limit.enabled=false")
@ImportRoseiotSecuredWebMvc
class UserCredentialControllerWebMvcTest {

    private static final UUID USER_ID = UUID.fromString("019f0000-0000-7000-8000-000000000001");

    @Autowired
    private MockMvcTester mvc;

    @MockitoBean
    private UserCredentialService credentialService;

    @MockitoBean
    private UserService userService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @MockitoBean
    private IdentityJwtAuthenticationConverter jwtAuthenticationConverter;

    @Test
    void shouldChangePasswordThroughAuthenticatedCredentialEndpoint() {
        User user = new User();
        user.setId(USER_ID);
        SecurityUser principal = new SecurityUser(user, List.of());

        assertThat(mvc.patch().uri("/api/user-credentials/me/password")
                .with(user(principal))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"currentPassword": "old-Passw0rd!", "newPassword": "new-Passw0rd!"}
                        """))
                .hasStatus(HttpStatus.NO_CONTENT);

        verify(credentialService).changePassword(USER_ID, "old-Passw0rd!", "new-Passw0rd!");
    }

    @Test
    void shouldRejectAnonymousPasswordChange() {
        assertThat(mvc.patch().uri("/api/user-credentials/me/password")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"currentPassword": "old-Passw0rd!", "newPassword": "new-Passw0rd!"}
                        """))
                .hasStatus(HttpStatus.UNAUTHORIZED);

        verifyNoInteractions(credentialService);
    }

    @Test
    void shouldAcceptResetPasswordByEmailWithOkAndNoBody() {
        assertThat(mvc.post().uri("/noauth/user-credentials/password-resets")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"email": "nobody@roseiot.io"}
                        """))
                .hasStatus(HttpStatus.OK);

        verify(credentialService).requestResetByEmail("nobody@roseiot.io");
    }

    @Test
    void shouldRejectResetPasswordByEmailWithBlankEmail() {
        assertThat(mvc.post().uri("/noauth/user-credentials/password-resets")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"email": ""}
                        """))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson()
                .extractingPath("$.code")
                .isEqualTo("validation_error");

        verifyNoInteractions(credentialService);
    }

    @Test
    void shouldAnswerValidResetTokenWithOkAndStatus() {
        given(credentialService.checkResetToken("plain-token")).willReturn(ResetTokenState.VALID);

        assertThat(mvc.get().uri("/noauth/user-credentials/password-resets?resetToken=plain-token"))
                .hasStatus(HttpStatus.OK)
                .bodyJson()
                .extractingPath("$.status")
                .isEqualTo("valid");
    }

    @Test
    void shouldAnswerValidResetTokenEvenWhenAuthorizationHeaderIsInvalid() {
        given(credentialService.checkResetToken("plain-token")).willReturn(ResetTokenState.VALID);

        assertThat(mvc.get()
                        .uri("/noauth/user-credentials/password-resets?resetToken=plain-token")
                        .header("Authorization", "Bearer not-a-valid-jwt"))
                .hasStatus(HttpStatus.OK)
                .bodyJson()
                .extractingPath("$.status")
                .isEqualTo("valid");
    }

    @Test
    void shouldAnswerInvalidResetTokenWithStatusInvalid() {
        given(credentialService.checkResetToken("bad")).willReturn(ResetTokenState.INVALID);

        assertThat(mvc.get().uri("/noauth/user-credentials/password-resets?resetToken=bad"))
                .hasStatus(HttpStatus.OK)
                .bodyJson()
                .extractingPath("$.status")
                .isEqualTo("invalid");
    }

    @Test
    void shouldAnswerExpiredResetTokenWithStatusExpired() {
        given(credentialService.checkResetToken("old")).willReturn(ResetTokenState.EXPIRED);

        assertThat(mvc.get().uri("/noauth/user-credentials/password-resets?resetToken=old"))
                .hasStatus(HttpStatus.OK)
                .bodyJson()
                .extractingPath("$.status")
                .isEqualTo("expired");
    }

    @Test
    void shouldResetPasswordWithTbShapedBody() {
        assertThat(mvc.put().uri("/noauth/user-credentials/password-resets")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"resetToken": "reset-token", "password": "Passw0rd!"}
                        """))
                .hasStatus(HttpStatus.NO_CONTENT);

        verify(credentialService).confirmPasswordReset("reset-token", "Passw0rd!");
    }

    @Test
    void shouldAnswerInvalidResetTokenOnPutWithServerError() {
        willThrow(new BusinessException(ApiErrorCode.INVALID_RESET_TOKEN))
                .given(credentialService)
                .confirmPasswordReset(any(), any());

        assertThat(mvc.put().uri("/noauth/user-credentials/password-resets")
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
    void shouldAnswerActivateEndpointsWithNotImplemented() {
        assertThat(mvc.get().uri("/noauth/users/activation?activateToken=t"))
                .hasStatus(HttpStatus.INTERNAL_SERVER_ERROR)
                .bodyJson()
                .extractingPath("$.code")
                .isEqualTo("not_implemented");

        assertThat(mvc.post().uri("/noauth/users/activation")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"activateToken": "t", "password": "Passw0rd!"}
                        """))
                .hasStatus(HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
