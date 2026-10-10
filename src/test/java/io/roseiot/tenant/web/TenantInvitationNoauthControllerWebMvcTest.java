package io.roseiot.tenant.web;

import io.roseiot.iam.infra.security.IdentityJwtAuthenticationConverter;
import io.roseiot.iam.infra.security.ImportRoseiotSecuredWebMvc;
import io.roseiot.iam.application.UserSessionService;
import io.roseiot.tenant.application.TenantInvitationService;
import io.roseiot.tenant.application.TenantInvitationService.InvitationTokenState;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

@WebMvcTest(controllers = TenantInvitationNoauthController.class)
@TestPropertySource(properties = "roseiot.rate-limit.enabled=false")
@ImportRoseiotSecuredWebMvc
class TenantInvitationNoauthControllerWebMvcTest {

    @Autowired
    private MockMvcTester mvc;

    @MockitoBean
    private TenantInvitationService invitationService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @MockitoBean
    private IdentityJwtAuthenticationConverter jwtAuthenticationConverter;

    @Test
    void shouldAnswerValidInvitationTokenWithStatus() {
        given(invitationService.checkInvitationToken("plain-token"))
                .willReturn(InvitationTokenState.VALID);

        assertThat(mvc.get().uri("/noauth/tenants/invitations?invitationToken=plain-token"))
                .hasStatus(HttpStatus.OK)
                .bodyJson()
                .extractingPath("$.status")
                .isEqualTo("valid");
    }

    @Test
    void shouldAcceptInvitationAndReturnSessionTokens() {
        given(invitationService.acceptInvitation("invite-token", "Passw0rd!"))
                .willReturn(new UserSessionService.SessionTokens("access-token", "refresh-token"));

        assertThat(mvc.post().uri("/noauth/tenants/invitations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"invitationToken": "invite-token", "password": "Passw0rd!"}
                                """))
                .hasStatus(HttpStatus.CREATED)
                .bodyJson()
                .extractingPath("$.accessToken")
                .isEqualTo("access-token");
    }
}
