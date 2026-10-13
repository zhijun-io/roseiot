package io.roseiot.iam.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import io.roseiot.iam.security.ImportRoseiotSecuredWebMvc;
import io.roseiot.shared.security.JwtAuthenticationConverter;
import io.roseiot.shared.security.JwtTokenFactory;
import io.roseiot.shared.security.SecurityUser;
import io.roseiot.tenant.TenantService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

@WebMvcTest(controllers = UserController.class)
@TestPropertySource(properties = "roseiot.rate-limit.enabled=false")
@ImportRoseiotSecuredWebMvc
class LoginAuthenticationWebMvcTest {
    @MockitoBean
    private TenantService tenantService;

    private static final Long USER_ID = System.nanoTime();
    private static final Long TENANT_ID = 42L;

    @Autowired
    private MockMvcTester mvc;

    @MockitoBean
    private UserService userService;

    @MockitoBean
    private UserCredentialService userCredentialService;

    @MockitoBean
    private AuthenticationManager authenticationManager;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @MockitoBean
    private JwtAuthenticationConverter jwtAuthenticationConverter;

    @MockitoBean
    private JwtTokenFactory jwtTokenFactory;

    @Test
    void loginReturnsOnlyAnAccessToken() {
        SecurityUser principal = new SecurityUser(USER_ID, TENANT_ID, "ops@roseiot.io", true, null, List.of());
        given(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .willReturn(UsernamePasswordAuthenticationToken.authenticated(principal, null, List.of()));
        given(jwtTokenFactory.createAccessToken(
                        org.mockito.ArgumentMatchers.eq(USER_ID), org.mockito.ArgumentMatchers.eq(TENANT_ID), any()))
                .willReturn("access-token-value");

        assertThat(mvc.post()
                        .uri("/api/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                        {"email":"ops@roseiot.io","password":"correct horse"}
                        """))
                .hasStatus(HttpStatus.OK)
                .hasHeader("Cache-Control", "no-cache, no-store, max-age=0, must-revalidate")
                .bodyJson()
                .isLenientlyEqualTo("""
                        {"accessToken":"access-token-value"}
                        """);
        verify(jwtTokenFactory)
                .createAccessToken(
                        org.mockito.ArgumentMatchers.eq(USER_ID), org.mockito.ArgumentMatchers.eq(TENANT_ID), any());
    }

    @Test
    void wrongCredentialsReturnStableUnauthorizedBody() {
        willThrow(new BadCredentialsException("invalid_credentials"))
                .given(authenticationManager)
                .authenticate(any(UsernamePasswordAuthenticationToken.class));
        assertThat(mvc.post()
                        .uri("/api/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                        {"email":"ops@roseiot.io","password":"wrong"}
                        """))
                .hasStatus(HttpStatus.UNAUTHORIZED)
                .bodyJson()
                .extractingPath("$.code")
                .isEqualTo("invalid_credentials");
        verifyNoInteractions(jwtTokenFactory);
    }

    @Test
    void rejectsBlankPasswordBeforeAuthentication() {
        assertThat(mvc.post()
                        .uri("/api/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                        {"email":"ops@roseiot.io","password":" "}
                        """))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson()
                .extractingPath("$.code")
                .isEqualTo("validation_error");
        verifyNoInteractions(authenticationManager);
    }

    @Test
    void rejectsMalformedLoginBody() {
        assertThat(mvc.post()
                        .uri("/api/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{"))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson()
                .extractingPath("$.code")
                .isEqualTo("bad_request");
        verifyNoInteractions(authenticationManager);
    }
}
