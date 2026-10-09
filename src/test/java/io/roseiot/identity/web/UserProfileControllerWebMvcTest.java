package io.roseiot.identity.web;

import io.roseiot.identity.application.UserProfileService;
import io.roseiot.identity.persistence.UserRoleMapper;
import io.roseiot.identity.infra.security.RoseiotWebMvcTestSupport;
import io.roseiot.identity.domain.IdentityJwtClaims;
import io.roseiot.identity.persistence.User;
import io.roseiot.identity.persistence.UserMapper;
import io.roseiot.shared.domain.ApiErrorCode;
import io.roseiot.shared.domain.BusinessException;
import io.roseiot.identity.infra.security.IdentityJwtAuthenticationConverter;
import io.roseiot.identity.infra.security.ImportRoseiotSecuredWebMvc;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

/**
 * Protocol contract of {@code /api/users/me/**}: the principal is taken from the bearer token, the
 * permission decision runs via {@code @PreAuthorize} and {@code @authz}; tenant JWT consistency is
 * enforced in {@link io.roseiot.identity.infra.security.IdentityJwtAuthenticationConverter}, not
 * re-checked here. A rejected password is reported
 * through the shared error body. The filter chain is the production one; only the token converter
 * and the services are replaced, so this slice never touches the database.
 */
@WebMvcTest(controllers = UserProfileController.class)
@TestPropertySource(properties = "roseiot.rate-limit.enabled=false")
@ImportRoseiotSecuredWebMvc
class UserProfileControllerWebMvcTest {

    private static final UUID USER_ID = UUID.fromString("019f0000-0000-7000-8000-000000000001");
    private static final UUID TENANT_ID = UUID.fromString("019f0000-0000-7000-8000-0000000000b1");
    private static final long AUTHORIZATION_VERSION = 3L;

    @Autowired
    private MockMvcTester mvc;

    @MockitoBean
    private UserProfileService accountService;

    @MockitoBean
    private UserMapper userMapper;

    @MockitoBean
    private UserRoleMapper userRoleMapper;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @MockitoBean
    private IdentityJwtAuthenticationConverter jwtAuthenticationConverter;

    @BeforeEach
    void grantTheTestPrincipalEveryPermission() {
        RoseiotWebMvcTestSupport.grantEveryPermission(
                userMapper, userRoleMapper, USER_ID, AUTHORIZATION_VERSION);
    }

    @Test
    void shouldAnswerProfileWithIdAndEmailOfTheTokenSubject() {
        given(accountService.getProfile(USER_ID)).willReturn(
                new UserProfileService.UserProfile(USER_ID, "ops@roseiot.io"));

        assertThat(mvc.get().uri("/api/users/me").with(jwt().jwt(this::systemToken)))
                .hasStatus(HttpStatus.OK)
                .bodyJson()
                .isLenientlyEqualTo("""
                        {
                          "id": "019f0000-0000-7000-8000-000000000001",
                          "email": "ops@roseiot.io"
                        }
                        """);
    }

    @Test
    void shouldAnswerProfileForTenantContextWhenTidMatchesSubject() {
        User tenantUser = new User();
        tenantUser.setId(USER_ID);
        tenantUser.setTenantId(TENANT_ID);
        tenantUser.setAuthorizationVersion(Math.toIntExact(AUTHORIZATION_VERSION));
        given(userMapper.selectById(USER_ID)).willReturn(tenantUser);
        given(accountService.getProfile(USER_ID)).willReturn(
                new UserProfileService.UserProfile(USER_ID, "tenant@roseiot.io"));

        assertThat(mvc.get().uri("/api/users/me").with(jwt().jwt(this::tenantToken)))
                .hasStatus(HttpStatus.OK)
                .bodyJson()
                .extractingPath("$.email")
                .isEqualTo("tenant@roseiot.io");
    }

    @Test
    void shouldRejectPrincipalWhenThePermissionIsMissing() {
        given(userRoleMapper.userHasPermission(
                        eq(USER_ID), eq(IdentityJwtClaims.PERMISSION_PROFILE_READ)))
                .willReturn(false);

        assertThat(mvc.get().uri("/api/users/me").with(jwt().jwt(this::systemToken)))
                .hasStatus(HttpStatus.FORBIDDEN)
                .bodyJson()
                .extractingPath("$.code")
                .isEqualTo("forbidden");

        verifyNoInteractions(accountService);
    }

    @Test
    void shouldRejectTokenWithoutAuthorizationVersionClaim() {
        assertThat(mvc.get().uri("/api/users/me")
                .with(jwt().jwt(builder -> {
                    systemToken(builder);
                    builder.claims(claims -> claims.remove(IdentityJwtClaims.AUTHORIZATION_VERSION));
                })))
                .hasStatus(HttpStatus.FORBIDDEN);

        verifyNoInteractions(accountService, userRoleMapper);
    }

    @Test
    void shouldAnswerPasswordChangeWithNoContent() {
        assertThat(mvc.patch().uri("/api/users/me/password")
                .with(jwt().jwt(this::systemToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"currentPassword": "old-Passw0rd!", "newPassword": "new-Passw0rd!"}
                        """))
                .hasStatus(HttpStatus.NO_CONTENT);

        verify(accountService).changePassword(USER_ID, "old-Passw0rd!", "new-Passw0rd!");
    }

    @Test
    void shouldRejectPasswordChangeWithBlankNewPasswordBeforeReachingTheService() {
        assertThat(mvc.patch().uri("/api/users/me/password")
                .with(jwt().jwt(this::systemToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"currentPassword": "old-Passw0rd!", "newPassword": ""}
                        """))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson()
                .extractingPath("$.code")
                .isEqualTo("validation_error");

        verifyNoInteractions(accountService);
    }

    @Test
    void shouldAnswerRejectedNewPasswordWithTheRuleThatFailed() {
        willThrow(new BusinessException(ApiErrorCode.VALIDATION_ERROR, "Password must be at least 12 characters"))
                .given(accountService)
                .changePassword(any(), any(), any());

        assertThat(mvc.patch().uri("/api/users/me/password")
                .with(jwt().jwt(this::systemToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"currentPassword": "old-Passw0rd!", "newPassword": "short"}
                        """))
                .hasStatus(HttpStatus.INTERNAL_SERVER_ERROR)
                .bodyJson()
                .isLenientlyEqualTo("""
                        {
                          "code": "validation_error",
                          "message": "Password must be at least 12 characters"
                        }
                        """);
    }

    private Jwt.Builder systemToken(Jwt.Builder builder) {
        Instant now = Instant.now();
        return builder.header("alg", "none")
                .subject(USER_ID.toString())
                .claim(IdentityJwtClaims.CONTEXT, IdentityJwtClaims.CONTEXT_SYSTEM)
                .claim(IdentityJwtClaims.AUTHORIZATION_VERSION, Math.toIntExact(AUTHORIZATION_VERSION))
                .issuedAt(now)
                .expiresAt(now.plusSeconds(60));
    }

    private Jwt.Builder tenantToken(Jwt.Builder builder) {
        Instant now = Instant.now();
        return builder.header("alg", "none")
                .subject(USER_ID.toString())
                .claim(IdentityJwtClaims.CONTEXT, IdentityJwtClaims.CONTEXT_TENANT)
                .claim(IdentityJwtClaims.TENANT_ID, TENANT_ID.toString())
                .claim(IdentityJwtClaims.AUTHORIZATION_VERSION, Math.toIntExact(AUTHORIZATION_VERSION))
                .issuedAt(now)
                .expiresAt(now.plusSeconds(60));
    }
}
