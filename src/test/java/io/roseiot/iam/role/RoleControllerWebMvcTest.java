package io.roseiot.iam.role;

import io.roseiot.iam.security.ImportRoseiotSecuredWebMvc;
import io.roseiot.shared.security.JwtAuthenticationConverter;
import io.roseiot.iam.security.RoseiotWebMvcTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@WebMvcTest(controllers = RoleController.class)
@TestPropertySource(properties = "roseiot.rate-limit.enabled=false")
@ImportRoseiotSecuredWebMvc
class RoleControllerWebMvcTest {

    private static final Long TENANT_ID = 42L;
    private static final Long ROLE_ID = 7L;

    @Autowired
    private MockMvcTester mvc;

    @MockitoBean
    private RoleService roleService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @MockitoBean
    private JwtAuthenticationConverter jwtAuthenticationConverter;

    @Test
    void shouldListTenantRoles() {
        given(roleService.list(org.mockito.ArgumentMatchers.eq(TENANT_ID), org.mockito.ArgumentMatchers.any(Pageable.class)))
                .willReturn(new PageImpl<>(List.of(new RoleInfo(ROLE_ID, "Operator", List.of("device.read"))),
                        PageRequest.of(0, 50, Sort.by("id")), 1));

        assertThat(mvc.get().uri("/roles").with(roleManager()))
                .hasStatus(HttpStatus.OK)
                .bodyJson()
                .extractingPath("$.content[0].actionCodes[0]")
                .isEqualTo("device.read");
    }

    @Test
    void shouldCreateRoleForAuthenticatedTenant() {
        given(roleService.create(TENANT_ID, "Operator", Set.of("device.read")))
                .willReturn(new RoleInfo(ROLE_ID, "Operator", List.of("device.read")));

        assertThat(mvc.post().uri("/roles")
                .with(roleManager())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Operator\",\"actionCodes\":[\"device.read\"]}"))
                .hasStatus(HttpStatus.OK)
                .bodyJson()
                .extractingPath("$.id")
                .isEqualTo(ROLE_ID.intValue());

        verify(roleService).create(TENANT_ID, "Operator", Set.of("device.read"));
    }

    @Test
    void shouldRejectRoleRequestsWithTooManyActionCodesBeforeCallingService() {
        String actionCodes = IntStream.range(0, 101)
                .mapToObj(index -> "\"device.read\"")
                .collect(java.util.stream.Collectors.joining(","));

        assertThat(mvc.post().uri("/roles")
                .with(roleManager())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Operator\",\"actionCodes\":[" + actionCodes + "]}"))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson()
                .extractingPath("$.code")
                .isEqualTo("validation_error");

        verifyNoInteractions(roleService);
    }

    @Test
    void shouldRequireRoleManagementPermission() {
        assertThat(mvc.get().uri("/roles").with(RoseiotWebMvcTestSupport.jwtWithTenantUser(this::tenantToken)))
                .hasStatus(HttpStatus.FORBIDDEN);
    }

    private org.springframework.test.web.servlet.request.RequestPostProcessor roleManager() {
        return RoseiotWebMvcTestSupport.jwtWithAuthorities(
                this::tenantToken, RoseiotWebMvcTestSupport.action("tenant.role.manage"));
    }

    private org.springframework.security.oauth2.jwt.Jwt.Builder tenantToken(
            org.springframework.security.oauth2.jwt.Jwt.Builder builder) {
        Instant now = Instant.now();
        return builder.header("alg", "none").subject("1001").claim("tid", TENANT_ID.toString())
                .issuedAt(now).expiresAt(now.plusSeconds(60));
    }
}
