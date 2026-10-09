package io.roseiot.identity.web;

import io.roseiot.identity.application.UserProfileService;
import io.roseiot.identity.infra.security.RoseiotWebMvcTestSupport;
import io.roseiot.identity.domain.IdentityJwtClaims;
import io.roseiot.shared.domain.ApiErrorCode;
import io.roseiot.shared.domain.BusinessException;
import io.roseiot.identity.infra.security.IdentityJwtAuthenticationConverter;
import io.roseiot.identity.infra.security.ImportRoseiotSecuredWebMvc;
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
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

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
    private JwtDecoder jwtDecoder;

    @MockitoBean
    private IdentityJwtAuthenticationConverter jwtAuthenticationConverter;

    @Test
    void shouldAnswerProfileWithIdAndEmailOfTheTokenSubject() {
        given(accountService.getProfile(USER_ID)).willReturn(
                new UserProfileService.UserProfile(USER_ID, "ops@roseiot.io"));

        assertThat(mvc.get().uri("/api/users/me").with(RoseiotWebMvcTestSupport.jwtWithSystemAdmin(this::systemToken)))
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
        given(accountService.getProfile(USER_ID)).willReturn(
                new UserProfileService.UserProfile(USER_ID, "tenant@roseiot.io"));

        assertThat(mvc.get().uri("/api/users/me").with(RoseiotWebMvcTestSupport.jwtWithTenantUser(this::tenantToken)))
                .hasStatus(HttpStatus.OK)
                .bodyJson()
                .extractingPath("$.email")
                .isEqualTo("tenant@roseiot.io");
    }

    @Test
    void shouldRejectPrincipalWhenThePermissionIsMissing() {
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

        verifyNoInteractions(accountService);
    }

    @Test
    void shouldAnswerPasswordChangeWithNoContent() {
        assertThat(mvc.patch().uri("/api/users/me/password")
                .with(RoseiotWebMvcTestSupport.jwtWithSystemAdmin(this::systemToken))
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
                .with(RoseiotWebMvcTestSupport.jwtWithSystemAdmin(this::systemToken))
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
                .with(RoseiotWebMvcTestSupport.jwtWithSystemAdmin(this::systemToken))
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
                .claim(IdentityJwtClaims.AUTHORIZATION_VERSION, Math.toIntExact(AUTHORIZATION_VERSION))
                .issuedAt(now)
                .expiresAt(now.plusSeconds(60));
    }

    private Jwt.Builder tenantToken(Jwt.Builder builder) {
        Instant now = Instant.now();
        return builder.header("alg", "none")
                .subject(USER_ID.toString())
                .claim("tid", TENANT_ID.toString())
                .claim(IdentityJwtClaims.AUTHORIZATION_VERSION, Math.toIntExact(AUTHORIZATION_VERSION))
                .issuedAt(now)
                .expiresAt(now.plusSeconds(60));
    }
}
