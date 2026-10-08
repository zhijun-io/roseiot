package io.roseiot.identity.web;

import io.roseiot.AbstractIt;
import io.roseiot.identity.application.BootstrapUserService;
import io.roseiot.identity.persistence.UserMapper;
import io.roseiot.identity.persistence.UserRefreshSessionMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.MediaType;

import static org.assertj.core.api.Assertions.assertThat;

class IdentityAuthTests extends AbstractIt {

    private static final String RAW_EMAIL = "Admin@Example.com";
    private static final String PASSWORD = "password1234";

    @Autowired
    BootstrapUserService bootstrapUserService;

    @Autowired
    UserMapper userMapper;

    @Autowired
    UserRefreshSessionMapper refreshSessionMapper;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @BeforeEach
    void seedPlatformUser() {
        refreshSessionMapper.delete(null);
        jdbcTemplate.update("DELETE FROM user_role");
        userMapper.delete(null);
        bootstrapUserService.bootstrap(RAW_EMAIL, PASSWORD);
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
                .uri("/api/users/me")
                .header("Authorization", "Bearer " + login.accessToken())
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody(IdentityMeController.IdentityMeResponse.class)
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
