package io.roseiot.identity.web;

import io.roseiot.AbstractIt;
import io.roseiot.identity.application.BootstrapUserService;
import io.roseiot.identity.infra.recovery.InMemoryRecoveryNotifier;
import io.roseiot.identity.persistence.UserMapper;
import io.roseiot.identity.persistence.UserRecoveryRequestMapper;
import io.roseiot.identity.persistence.UserRefreshSessionMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

class IdentityAuthMockMvcTests extends AbstractIt {

    private static final String RAW_EMAIL = "Admin@Example.com";
    private static final String PASSWORD = "password1234";
    private static final String NEW_PASSWORD = "newPassword5678";
    private static final String RECOVERED_PASSWORD = "recoveredPass9012";

    @Autowired
    BootstrapUserService bootstrapUserService;

    @Autowired
    UserMapper userMapper;

    @Autowired
    UserRefreshSessionMapper refreshSessionMapper;

    @Autowired
    UserRecoveryRequestMapper recoveryRequestMapper;

    @Autowired
    InMemoryRecoveryNotifier recoveryNotifier;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @BeforeEach
    void seedPlatformUser() {
        recoveryRequestMapper.delete(null);
        refreshSessionMapper.delete(null);
        jdbcTemplate.update("DELETE FROM user_role");
        userMapper.delete(null);
        recoveryNotifier.clear();
        bootstrapUserService.bootstrap(RAW_EMAIL, PASSWORD);
    }

    @Test
    void platformMeRequiresAuthenticationWithStableJsonError() {
        mvc.get()
                .uri("/api/identity/me")
                .exchange()
                .assertThat()
                .hasStatus(HttpStatus.UNAUTHORIZED)
                .bodyText()
                .contains("\"code\":\"unauthorized\"")
                .contains("\"message\"");
    }

    @Test
    void wrongPasswordAndUnknownEmailShareInvalidCredentialsResponse() {
        var unknownEmail = postLogin("nobody@example.com", PASSWORD);
        var wrongPassword = postLogin("admin@example.com", "wrongPassword1");

        unknownEmail.assertThat().hasStatus(HttpStatus.UNAUTHORIZED);
        wrongPassword.assertThat().hasStatus(HttpStatus.UNAUTHORIZED);

        unknownEmail.assertThat().bodyText().isEqualTo(wrongPassword.assertThat().bodyText().actual());
        unknownEmail.assertThat().bodyText().doesNotContainIgnoringCase("not found");
        unknownEmail
                .assertThat()
                .bodyJson()
                .extractingPath("$.code")
                .asString()
                .isEqualTo("invalid_credentials");
    }

    @Test
    void refreshRotatesTokenAndRejectsReplayByRevokingFamily() {
        AuthController.LoginResponse login =
                postLogin("admin@example.com", PASSWORD)
                        .assertThat()
                        .hasStatusOk()
                        .bodyJson()
                        .convertTo(AuthController.LoginResponse.class)
                        .actual();

        AuthController.LoginResponse rotated =
                postRefresh(login.refreshToken())
                        .assertThat()
                        .hasStatusOk()
                        .bodyJson()
                        .convertTo(AuthController.LoginResponse.class)
                        .actual();

        postRefresh(login.refreshToken())
                .assertThat()
                .hasStatus(HttpStatus.UNAUTHORIZED)
                .bodyJson()
                .extractingPath("$.code")
                .asString()
                .isEqualTo("invalid_refresh_token");

        postRefresh(rotated.refreshToken())
                .assertThat()
                .hasStatus(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void logoutRevokesCurrentRefreshSession() {
        AuthController.LoginResponse login =
                postLogin("admin@example.com", PASSWORD)
                        .assertThat()
                        .hasStatusOk()
                        .bodyJson()
                        .convertTo(AuthController.LoginResponse.class)
                        .actual();

        postLogout(login.accessToken()).assertThat().hasStatus(HttpStatus.NO_CONTENT);

        postRefresh(login.refreshToken())
                .assertThat()
                .hasStatus(HttpStatus.UNAUTHORIZED)
                .bodyJson()
                .extractingPath("$.code")
                .asString()
                .isEqualTo("invalid_refresh_token");
    }

    @Test
    void recoveryRequestReturnsSameResponseForUnknownAndRegisteredEmail() {
        var registered = postRecoveryRequest("admin@example.com");
        var unknown = postRecoveryRequest("nobody@example.com");

        registered.assertThat().hasStatus(HttpStatus.ACCEPTED);
        unknown.assertThat().hasStatus(HttpStatus.ACCEPTED);
        registered.assertThat().bodyText().isEqualTo(unknown.assertThat().bodyText().actual());
        registered
                .assertThat()
                .bodyJson()
                .extractingPath("$.message")
                .asString()
                .isEqualTo("If an account exists, recovery instructions were sent");

        recoveryNotifier.lastForEmail("admin@example.com").orElseThrow();
        recoveryNotifier.lastForEmail("nobody@example.com").isEmpty();
    }

    @Test
    void recoveryConfirmSetsNewPasswordRevokesRefreshAndRejectsTokenReuse() {
        AuthController.LoginResponse login =
                postLogin("admin@example.com", PASSWORD)
                        .assertThat()
                        .hasStatusOk()
                        .bodyJson()
                        .convertTo(AuthController.LoginResponse.class)
                        .actual();

        postRecoveryRequest("admin@example.com").assertThat().hasStatus(HttpStatus.ACCEPTED);

        String token = recoveryNotifier
                .lastForEmail("admin@example.com")
                .orElseThrow()
                .token();

        postRecoveryConfirm(token, RECOVERED_PASSWORD).assertThat().hasStatus(HttpStatus.NO_CONTENT);

        postLogin("admin@example.com", RECOVERED_PASSWORD).assertThat().hasStatusOk();
        postLogin("admin@example.com", PASSWORD).assertThat().hasStatus(HttpStatus.UNAUTHORIZED);

        postRefresh(login.refreshToken())
                .assertThat()
                .hasStatus(HttpStatus.UNAUTHORIZED)
                .bodyJson()
                .extractingPath("$.code")
                .asString()
                .isEqualTo("invalid_refresh_token");

        postRecoveryConfirm(token, "anotherPass3456")
                .assertThat()
                .hasStatus(HttpStatus.UNAUTHORIZED)
                .bodyJson()
                .extractingPath("$.code")
                .asString()
                .isEqualTo("invalid_recovery_token");
    }

    @Test
    void changePasswordRevokesRefreshSessions() {
        AuthController.LoginResponse login =
                postLogin("admin@example.com", PASSWORD)
                        .assertThat()
                        .hasStatusOk()
                        .bodyJson()
                        .convertTo(AuthController.LoginResponse.class)
                        .actual();

        patchPassword(login.accessToken(), PASSWORD, NEW_PASSWORD).assertThat().hasStatus(HttpStatus.NO_CONTENT);

        postRefresh(login.refreshToken()).assertThat().hasStatus(HttpStatus.UNAUTHORIZED);

        postLogin("admin@example.com", NEW_PASSWORD).assertThat().hasStatusOk();
    }

    private org.springframework.test.web.servlet.assertj.MvcTestResult postRecoveryRequest(String email) {
        return mvc.post()
                .uri("/api/identity/auth/recovery/request")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"email":"%s"}
                        """
                        .formatted(email))
                .exchange();
    }

    private org.springframework.test.web.servlet.assertj.MvcTestResult postRecoveryConfirm(
            String token, String newPassword) {
        return mvc.post()
                .uri("/api/identity/auth/recovery/confirm")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                        """
                        {"token":"%s","newPassword":"%s"}
                        """
                                .formatted(token, newPassword))
                .exchange();
    }

    private org.springframework.test.web.servlet.assertj.MvcTestResult postLogin(String email, String password) {
        return mvc.post()
                .uri("/api/identity/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(loginJson(email, password))
                .exchange();
    }

    private org.springframework.test.web.servlet.assertj.MvcTestResult postRefresh(String refreshToken) {
        return mvc.post()
                .uri("/api/identity/auth/token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(refreshJson(refreshToken))
                .exchange();
    }

    private org.springframework.test.web.servlet.assertj.MvcTestResult postLogout(String accessToken) {
        return mvc.post()
                .uri("/api/identity/auth/logout")
                .header("Authorization", "Bearer " + accessToken)
                .exchange();
    }

    private org.springframework.test.web.servlet.assertj.MvcTestResult patchPassword(
            String accessToken, String currentPassword, String newPassword) {
        return mvc.patch()
                .uri("/api/identity/me/password")
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                        """
                        {"currentPassword":"%s","newPassword":"%s"}
                        """
                                .formatted(currentPassword, newPassword))
                .exchange();
    }

    private static String loginJson(String email, String password) {
        return """
                {"email":"%s","password":"%s"}
                """
                .formatted(email, password);
    }

    private static String refreshJson(String refreshToken) {
        return """
                {"refreshToken":"%s"}
                """
                .formatted(refreshToken);
    }
}
