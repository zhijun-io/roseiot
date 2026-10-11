package io.roseiot.iam.user;

import io.roseiot.iam.user.UserCredentialService.ResetTokenState;
import io.roseiot.shared.ApiErrorCode;
import io.roseiot.shared.BusinessException;
import io.roseiot.shared.security.JwtAuthenticationConverter;
import io.roseiot.iam.security.ImportRoseiotSecuredWebMvc;
import io.roseiot.shared.security.SecurityUser;
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
        SecurityUser principal = new SecurityUser(USER_ID, null, "user@roseiot.io", true, null, List.of());

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
    void shouldAnswerValidResetTokenWithoutPuttingItInTheUrl() {
        given(credentialService.checkResetToken("plain-token")).willReturn(ResetTokenState.VALID);

        assertThat(mvc.post().uri("/password-resets/validations")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"plain-token\"}"))
                .hasStatus(HttpStatus.OK)
                .hasHeader("Cache-Control", "no-store")
                .bodyJson()
                .extractingPath("$.status")
                .isEqualTo("valid");
    }

    @Test
    void shouldAllowAnonymousResetTokenValidationEvenWhenAuthorizationHeaderIsInvalid() {
        given(credentialService.checkResetToken("plain-token")).willReturn(ResetTokenState.VALID);

        assertThat(mvc.post()
                        .uri("/password-resets/validations")
                        .header("Authorization", "Bearer not-a-valid-jwt")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"plain-token\"}"))
                .hasStatus(HttpStatus.OK)
                .bodyJson()
                .extractingPath("$.status")
                .isEqualTo("valid");
    }

    @Test
    void shouldAnswerInvalidResetTokenWithStatusInvalid() {
        given(credentialService.checkResetToken("bad")).willReturn(ResetTokenState.INVALID);

        assertThat(mvc.post().uri("/password-resets/validations")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"bad\"}"))
                .hasStatus(HttpStatus.OK)
                .bodyJson()
                .extractingPath("$.status")
                .isEqualTo("invalid");
    }

    @Test
    void shouldAnswerExpiredResetTokenWithStatusExpired() {
        given(credentialService.checkResetToken("old")).willReturn(ResetTokenState.EXPIRED);

        assertThat(mvc.post().uri("/password-resets/validations")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"old\"}"))
                .hasStatus(HttpStatus.OK)
                .bodyJson()
                .extractingPath("$.status")
                .isEqualTo("expired");
    }

    @Test
    void shouldResetPasswordWithTokenInTheRequestBody() {
        assertThat(mvc.post().uri("/password-resets/confirmations")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"token": "reset-token", "password": "Passw0rd!"}
                        """))
                .hasStatus(HttpStatus.OK)
                .hasHeader("Cache-Control", "no-store");

        verify(credentialService).confirmPasswordReset("reset-token", "Passw0rd!");
    }

    @Test
    void shouldAnswerInvalidResetTokenWithBadRequest() {
        willThrow(new BusinessException(ApiErrorCode.INVALID_RESET_TOKEN))
                .given(credentialService)
                .confirmPasswordReset(any(), any());

        assertThat(mvc.post().uri("/password-resets/confirmations")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"token": "used-token", "password": "Passw0rd!"}
                        """))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .hasHeader("Cache-Control", "no-store")
                .bodyJson()
                .extractingPath("$.code")
                .isEqualTo("invalid_reset_token");
    }

}
