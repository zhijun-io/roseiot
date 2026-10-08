package io.roseiot.platform.auth.web;

import io.roseiot.AbstractIt;
import io.roseiot.platform.bootstrap.BootstrapPlatformAdminService;
import io.roseiot.platform.persistence.PlatformAdminMapper;
import io.roseiot.platform.persistence.PlatformAdminRefreshSessionMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import static org.assertj.core.api.Assertions.assertThat;

class PlatformAdminAuthTests extends AbstractIt {

    private static final String RAW_EMAIL = "Admin@Example.com";
    private static final String PASSWORD = "password1234";

    @Autowired
    BootstrapPlatformAdminService bootstrapPlatformAdminService;

    @Autowired
    PlatformAdminMapper platformAdminMapper;

    @Autowired
    PlatformAdminRefreshSessionMapper refreshSessionMapper;

    @BeforeEach
    void seedPlatformAdmin() {
        refreshSessionMapper.delete(null);
        platformAdminMapper.delete(null);
        bootstrapPlatformAdminService.bootstrap(RAW_EMAIL, PASSWORD);
    }

    @Test
    void loginThenProfileWithAccessToken() {
        AuthController.LoginResponse login =
                restTestClient
                        .post()
                        .uri("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(loginJson("admin@example.com", PASSWORD))
                        .exchange()
                        .expectStatus()
                        .isOk()
                        .expectBody(AuthController.LoginResponse.class)
                        .returnResult()
                        .getResponseBody();

        assertThat(login).isNotNull();
        assertThat(login.accessToken()).isNotBlank();

        restTestClient
                .get()
                .uri("/api/platform/me")
                .header("Authorization", "Bearer " + login.accessToken())
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody(PlatformMeController.PlatformMeResponse.class)
                .value(
                        profile -> {
                            assertThat(profile.email()).isEqualTo("admin@example.com");
                            assertThat(profile.id()).isNotNull();
                        });
    }

    private static String loginJson(String email, String password) {
        return """
                {"email":"%s","password":"%s"}
                """
                .formatted(email, password);
    }
}
