package io.roseiot.iam.resource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import io.roseiot.iam.security.ImportRoseiotSecuredWebMvc;
import io.roseiot.iam.security.RoseiotWebMvcTestSupport;
import io.roseiot.shared.security.JwtAuthenticationConverter;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

@WebMvcTest(controllers = ResourceController.class)
@TestPropertySource(properties = "roseiot.rate-limit.enabled=false")
@ImportRoseiotSecuredWebMvc
class ResourceControllerWebMvcTest {

    @Autowired
    private MockMvcTester mvc;

    @MockitoBean
    private ResourceService resourceService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @MockitoBean
    private JwtAuthenticationConverter jwtAuthenticationConverter;

    @Test
    void shouldReturnReadOnlyCatalogToAuthenticatedUsers() {
        given(resourceService.list(PageRequest.of(0, 50, Sort.by("id"))))
                .willReturn(new PageImpl<>(
                        List.of(new Resource(
                                1L,
                                1L,
                                "device.read",
                                "Read devices",
                                ResourceType.ACTION,
                                null,
                                ResourceStatus.ACTIVE,
                                null,
                                null,
                                1)),
                        PageRequest.of(0, 50, Sort.by("id")),
                        1));

        assertThat(mvc.get().uri("/api/resources").with(RoseiotWebMvcTestSupport.jwtWithTenantUser(this::tenantToken)))
                .hasStatus(HttpStatus.OK)
                .bodyJson()
                .extractingPath("$.content[0].code")
                .isEqualTo("device.read");

        assertThat(mvc.get().uri("/api/resources").with(RoseiotWebMvcTestSupport.jwtWithTenantUser(this::tenantToken)))
                .bodyJson()
                .extractingPath("$.content[0].tenantId")
                .isEqualTo(1);
        assertThat(mvc.get().uri("/api/resources").with(RoseiotWebMvcTestSupport.jwtWithTenantUser(this::tenantToken)))
                .bodyJson()
                .extractingPath("$.content[0].status")
                .isEqualTo("ACTIVE");
    }

    @Test
    void shouldRequireAuthenticationForCatalog() {
        assertThat(mvc.get().uri("/api/resources")).hasStatus(HttpStatus.UNAUTHORIZED);
    }

    private Jwt.Builder tenantToken(Jwt.Builder builder) {
        Instant now = Instant.now();
        return builder.header("alg", "none")
                .subject("1001")
                .claim("tid", "42")
                .issuedAt(now)
                .expiresAt(now.plusSeconds(60));
    }
}
