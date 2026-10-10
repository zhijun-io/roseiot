package io.roseiot.iam.web;

import io.roseiot.iam.application.UserService;
import io.roseiot.iam.infra.security.AuthenticationExceptionMapping;
import io.roseiot.iam.infra.security.IdentityJwtAuthenticationConverter;
import io.roseiot.iam.infra.security.ImportRoseiotSecuredWebMvc;
import io.roseiot.iam.infra.security.JwtTokenFactory;
import io.roseiot.iam.infra.security.SecurityUser;
import io.roseiot.iam.persistence.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@WebMvcTest(controllers = UserController.class)
@TestPropertySource(properties = "roseiot.rate-limit.enabled=false")
@ImportRoseiotSecuredWebMvc
class LoginAuthenticationWebMvcTest {
    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID TENANT_ID = UUID.randomUUID();

    @Autowired private MockMvcTester mvc;
    @MockitoBean private UserService userService;
    @MockitoBean private AuthenticationManager identityAuthenticationManager;
    @MockitoBean private JwtDecoder jwtDecoder;
    @MockitoBean private IdentityJwtAuthenticationConverter jwtAuthenticationConverter;
    @MockitoBean private JwtTokenFactory jwtTokenFactory;

    @Test
    void loginReturnsOnlyAnAccessToken() {
        User user = new User();
        user.setId(USER_ID);
        user.setTenantId(TENANT_ID);
        user.setEmail("ops@roseiot.io");
        SecurityUser principal = new SecurityUser(user, List.of());
        given(identityAuthenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .willReturn(UsernamePasswordAuthenticationToken.authenticated(principal, null, List.of()));
        given(jwtTokenFactory.createAccessToken(org.mockito.ArgumentMatchers.eq(USER_ID),
                org.mockito.ArgumentMatchers.eq(TENANT_ID), any()))
                .willReturn("access-token-value");

        assertThat(mvc.post().uri("/login").contentType(MediaType.APPLICATION_JSON).content("""
                        {"email":"ops@roseiot.io","password":"correct horse"}
                        """))
                .hasStatus(HttpStatus.OK).bodyJson().isLenientlyEqualTo("""
                        {"accessToken":"access-token-value"}
                        """);
        verify(jwtTokenFactory).createAccessToken(org.mockito.ArgumentMatchers.eq(USER_ID),
                org.mockito.ArgumentMatchers.eq(TENANT_ID), any());
    }

    @Test
    void wrongCredentialsReturnStableUnauthorizedBody() {
        willThrow(AuthenticationExceptionMapping.invalidCredentials()).given(identityAuthenticationManager)
                .authenticate(any(UsernamePasswordAuthenticationToken.class));
        assertThat(mvc.post().uri("/login").contentType(MediaType.APPLICATION_JSON).content("""
                        {"email":"ops@roseiot.io","password":"wrong"}
                        """))
                .hasStatus(HttpStatus.UNAUTHORIZED).bodyJson().extractingPath("$.code").isEqualTo("invalid_credentials");
        verifyNoInteractions(jwtTokenFactory);
    }

    @Test
    void rejectsBlankPasswordBeforeAuthentication() {
        assertThat(mvc.post().uri("/login").contentType(MediaType.APPLICATION_JSON).content("""
                        {"email":"ops@roseiot.io","password":" "}
                        """))
                .hasStatus(HttpStatus.BAD_REQUEST).bodyJson().extractingPath("$.code").isEqualTo("validation_error");
        verifyNoInteractions(identityAuthenticationManager);
    }

    @Test
    void rejectsMalformedLoginBody() {
        assertThat(mvc.post().uri("/login").contentType(MediaType.APPLICATION_JSON).content("{"))
                .hasStatus(HttpStatus.BAD_REQUEST).bodyJson().extractingPath("$.code").isEqualTo("bad_request");
        verifyNoInteractions(identityAuthenticationManager);
    }
}
