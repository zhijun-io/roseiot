package io.roseiot.iam.user;

import io.roseiot.iam.user.UserCredentialService.ResetTokenState;
import io.roseiot.shared.ApiErrorCode;
import io.roseiot.shared.BusinessException;
import io.roseiot.iam.security.JwtAuthenticationConverter;
import io.roseiot.iam.security.ImportRoseiotSecuredWebMvc;
import io.roseiot.iam.security.SecurityUser;
import io.roseiot.tenant.TenantService;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;

@WebMvcTest(controllers = UserController.class)
@TestPropertySource(properties = "roseiot.rate-limit.enabled=false")
@ImportRoseiotSecuredWebMvc
class UserControllerCredentialWebMvcTest {
    @MockitoBean
    private TenantService tenantService;


    private static final Long USER_ID = 1003L;

    @Autowired
    private MockMvcTester mvc;

    @MockitoBean
    private UserCredentialService credentialService;

    @MockitoBean
    private UserService userService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @MockitoBean
    private JwtAuthenticationConverter jwtAuthenticationConverter;

    @Test
    void shouldChangePasswordThroughAuthenticatedCredentialEndpoint() {
        User user = new User(USER_ID, null, null, null, UserStatus.ACTIVE);
        SecurityUser principal = new SecurityUser(user, null, List.of());

        assertThat(mvc.put().uri("/me/password")
                .with(user(principal))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"currentPassword": "old-Passw0rd!", "newPassword": "new-Passw0rd!"}
                        """))
                .hasStatus(HttpStatus.OK);

        verify(credentialService).changePassword(USER_ID, "old-Passw0rd!", "new-Passw0rd!");
    }

    @Test
    void shouldRejectAnonymousPasswordChange() {
        assertThat(mvc.put().uri("/me/password")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"currentPassword": "old-Passw0rd!", "newPassword": "new-Passw0rd!"}
                        """))
                .hasStatus(HttpStatus.UNAUTHORIZED);

        verifyNoInteractions(credentialService);
    }

    @Test
    void shouldAcceptResetPasswordByEmailWithOkAndNoBody() {
        assertThat(mvc.post().uri("/password-resets")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"email": "nobody@roseiot.io"}
                        """))
                .hasStatus(HttpStatus.OK);

        verify(credentialService).requestResetByEmail("nobody@roseiot.io");
    }

    @Test
    void shouldRejectResetPasswordByEmailWithBlankEmail() {
        assertThat(mvc.post().uri("/password-resets")
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

        assertThat(mvc.get().uri("/password-resets/plain-token"))
                .hasStatus(HttpStatus.OK)
                .bodyJson()
                .extractingPath("$.status")
                .isEqualTo("valid");
    }

    @Test
    void shouldAnswerValidResetTokenEvenWhenAuthorizationHeaderIsInvalid() {
        given(credentialService.checkResetToken("plain-token")).willReturn(ResetTokenState.VALID);

        assertThat(mvc.get()
                        .uri("/password-resets/plain-token")
                        .header("Authorization", "Bearer not-a-valid-jwt"))
                .hasStatus(HttpStatus.OK)
                .bodyJson()
                .extractingPath("$.status")
                .isEqualTo("valid");
    }

    @Test
    void shouldAnswerInvalidResetTokenWithStatusInvalid() {
        given(credentialService.checkResetToken("bad")).willReturn(ResetTokenState.INVALID);

        assertThat(mvc.get().uri("/password-resets/bad"))
                .hasStatus(HttpStatus.OK)
                .bodyJson()
                .extractingPath("$.status")
                .isEqualTo("invalid");
    }

    @Test
    void shouldAnswerExpiredResetTokenWithStatusExpired() {
        given(credentialService.checkResetToken("old")).willReturn(ResetTokenState.EXPIRED);

        assertThat(mvc.get().uri("/password-resets/old"))
                .hasStatus(HttpStatus.OK)
                .bodyJson()
                .extractingPath("$.status")
                .isEqualTo("expired");
    }

    @Test
    void shouldResetPasswordWithTbShapedBody() {
        assertThat(mvc.put().uri("/password-resets/reset-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"password": "Passw0rd!"}
                        """))
                .hasStatus(HttpStatus.OK);

        verify(credentialService).confirmPasswordReset("reset-token", "Passw0rd!");
    }

    @Test
    void shouldAnswerInvalidResetTokenOnPutWithServerError() {
        willThrow(new BusinessException(ApiErrorCode.INVALID_RESET_TOKEN))
                .given(credentialService)
                .confirmPasswordReset(any(), any());

        assertThat(mvc.put().uri("/password-resets/used-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"password": "Passw0rd!"}
                        """))
                .hasStatus(HttpStatus.INTERNAL_SERVER_ERROR)
                .bodyJson()
                .extractingPath("$.code")
                .isEqualTo("invalid_reset_token");
    }

}
