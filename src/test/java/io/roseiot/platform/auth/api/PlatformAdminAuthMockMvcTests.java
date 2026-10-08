package io.roseiot.platform.auth.api;

import io.roseiot.AbstractIt;
import io.roseiot.platform.bootstrap.BootstrapPlatformAdminUseCase;
import io.roseiot.platform.persistence.PlatformAdminMapper;
import io.roseiot.platform.persistence.PlatformAdminRefreshSessionMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

class PlatformAdminAuthMockMvcTests extends AbstractIt {

    private static final String RAW_EMAIL = "Admin@Example.com";
    private static final String PASSWORD = "password1234";

    @Autowired
    BootstrapPlatformAdminUseCase bootstrapPlatformAdminUseCase;

    @Autowired
    PlatformAdminMapper platformAdminMapper;

    @Autowired
    PlatformAdminRefreshSessionMapper refreshSessionMapper;

    @BeforeEach
    void seedPlatformAdmin() {
        refreshSessionMapper.delete(null);
        platformAdminMapper.delete(null);
        bootstrapPlatformAdminUseCase.bootstrap(RAW_EMAIL, PASSWORD);
    }

    @Test
    void platformMeRequiresAuthenticationWithStableJsonError() {
        mvc.get()
                .uri("/api/platform/me")
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
        LoginResponse login =
                postLogin("admin@example.com", PASSWORD)
                        .assertThat()
                        .hasStatusOk()
                        .bodyJson()
                        .convertTo(LoginResponse.class)
                        .actual();

        LoginResponse rotated =
                postRefresh(login.refreshToken())
                        .assertThat()
                        .hasStatusOk()
                        .bodyJson()
                        .convertTo(LoginResponse.class)
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

    private org.springframework.test.web.servlet.assertj.MvcTestResult postLogin(String email, String password) {
        return mvc.post()
                .uri("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(loginJson(email, password))
                .exchange();
    }

    private org.springframework.test.web.servlet.assertj.MvcTestResult postRefresh(String refreshToken) {
        return mvc.post()
                .uri("/api/auth/token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(refreshJson(refreshToken))
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
