package io.roseiot.identity.web;

import io.roseiot.AbstractIt;
import io.roseiot.identity.application.BootstrapUserService;
import io.roseiot.identity.domain.IdentityJwtClaims;
import io.roseiot.identity.persistence.User;
import io.roseiot.identity.persistence.UserMapper;
import io.roseiot.identity.persistence.UserRecoveryRequestMapper;
import io.roseiot.identity.persistence.UserRefreshSessionMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Slice 3：JWT ctx/azv 与 RBAC 守门（AC-11～AC-13）。 */
class IdentityAuthJwtMockMvcTests extends AbstractIt {

    private static final String PASSWORD = "password1234";

    @Autowired
    BootstrapUserService bootstrapUserService;

    @Autowired
    UserMapper userMapper;

    @Autowired
    UserRefreshSessionMapper refreshSessionMapper;

    @Autowired
    UserRecoveryRequestMapper recoveryRequestMapper;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    JwtDecoder jwtDecoder;

    @Autowired
    JwtEncoder jwtEncoder;

    @BeforeEach
    void seedPlatformUser() {
        recoveryRequestMapper.delete(null);
        refreshSessionMapper.delete(null);
        jdbcTemplate.update("DELETE FROM user_role");
        userMapper.delete(null);
        bootstrapUserService.bootstrap("admin@example.com", PASSWORD);
    }

    @Test
    void loginAccessTokenContainsPlatformContextAndAuthorizationVersion() {
        AuthController.LoginResponse login = postLogin("admin@example.com", PASSWORD)
                .assertThat()
                .hasStatusOk()
                .bodyJson()
                .convertTo(AuthController.LoginResponse.class)
                .actual();

        var jwt = jwtDecoder.decode(login.accessToken());
        assertThat(jwt.getClaimAsString(IdentityJwtClaims.CONTEXT)).isEqualTo(IdentityJwtClaims.CONTEXT_PLATFORM);
        Number azv = jwt.getClaim(IdentityJwtClaims.AUTHORIZATION_VERSION);
        assertThat(azv.longValue()).isEqualTo(1L);
    }

    @Test
    void changePasswordIncrementsAuthorizationVersionAndInvalidatesPriorAccessToken() {
        AuthController.LoginResponse login = postLogin("admin@example.com", PASSWORD)
                .assertThat()
                .hasStatusOk()
                .bodyJson()
                .convertTo(AuthController.LoginResponse.class)
                .actual();

        patchPassword(login.accessToken(), PASSWORD, "newPassword5678").assertThat().hasStatus(HttpStatus.NO_CONTENT);

        User user = userMapper.selectOne(null);
        assertThat(user.getAuthorizationVersion()).isEqualTo(2);

        mvc.get()
                .uri("/api/identity/me")
                .header("Authorization", "Bearer " + login.accessToken())
                .exchange()
                .assertThat()
                .hasStatus(HttpStatus.UNAUTHORIZED);

        postRefresh(login.refreshToken()).assertThat().hasStatus(HttpStatus.UNAUTHORIZED);
    }

    private org.springframework.test.web.servlet.assertj.MvcTestResult postRefresh(String refreshToken) {
        return mvc.post()
                .uri("/api/identity/auth/token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                        """
                        {"refreshToken":"%s"}
                        """
                                .formatted(refreshToken))
                .exchange();
    }

    @Test
    void tenantContextJwtIsForbiddenOnPlatformMe() {
        User user = userMapper.selectOne(null);
        UUID sessionId = UUID.randomUUID();
        String tenantToken = jwtEncoder
                .encode(JwtEncoderParameters.from(
                        JwsHeader.with(MacAlgorithm.HS256).build(),
                        JwtClaimsSet.builder()
                                .subject(user.getId().toString())
                                .claim("sid", sessionId.toString())
                                .claim(IdentityJwtClaims.CONTEXT, IdentityJwtClaims.CONTEXT_TENANT)
                                .claim(IdentityJwtClaims.AUTHORIZATION_VERSION, user.getAuthorizationVersion())
                                .issuedAt(Instant.now())
                                .expiresAt(Instant.now().plus(15, ChronoUnit.MINUTES))
                                .build()))
                .getTokenValue();

        mvc.get()
                .uri("/api/identity/me")
                .header("Authorization", "Bearer " + tenantToken)
                .exchange()
                .assertThat()
                .hasStatus(HttpStatus.FORBIDDEN)
                .bodyJson()
                .extractingPath("$.code")
                .asString()
                .isEqualTo("forbidden");
    }

    private org.springframework.test.web.servlet.assertj.MvcTestResult postLogin(String email, String password) {
        return mvc.post()
                .uri("/api/identity/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                        """
                        {"email":"%s","password":"%s"}
                        """
                                .formatted(email, password))
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
}
