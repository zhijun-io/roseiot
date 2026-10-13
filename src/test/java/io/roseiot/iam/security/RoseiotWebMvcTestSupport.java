package io.roseiot.iam.security;

import io.roseiot.shared.security.SecurityUser;
import java.time.Instant;
import java.util.Arrays;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/** Authorities in WebMvc slices are ACTION codes only; personal endpoints need authentication, not actions. */
public final class RoseiotWebMvcTestSupport {

    private RoseiotWebMvcTestSupport() {}

    public static SimpleGrantedAuthority action(String code) {
        return new SimpleGrantedAuthority(code);
    }

    public static SimpleGrantedAuthority[] platformAdminAuthorities() {
        return Stream.of("tenant.create", "tenant.disable", "tenant.enable", "tenant.read", "system.setting.manage")
                .map(SimpleGrantedAuthority::new)
                .toArray(SimpleGrantedAuthority[]::new);
    }

    public static SimpleGrantedAuthority[] tenantAdminAuthorities() {
        return Stream.of("tenant.user.manage").map(SimpleGrantedAuthority::new).toArray(SimpleGrantedAuthority[]::new);
    }

    public static RequestPostProcessor jwtWithTenantUser(Consumer<Jwt.Builder> jwtCustomizer) {
        return jwtWithAuthorities(jwtCustomizer);
    }

    /** @deprecated kept for readability: platform administrators of the default tenant. */
    public static RequestPostProcessor jwtWithSystemAdmin(Consumer<Jwt.Builder> jwtCustomizer) {
        return jwtWithAuthorities(jwtCustomizer, platformAdminAuthorities());
    }

    public static RequestPostProcessor jwtWithAllPermissions(Consumer<Jwt.Builder> jwtCustomizer) {
        return jwtWithSystemAdmin(jwtCustomizer);
    }

    public static RequestPostProcessor jwtWithTenantAdmin(Consumer<Jwt.Builder> jwtCustomizer) {
        return jwtWithAuthorities(jwtCustomizer, tenantAdminAuthorities());
    }

    public static RequestPostProcessor jwtWithAuthorities(
            Consumer<Jwt.Builder> jwtCustomizer, SimpleGrantedAuthority... authorities) {
        return request -> {
            Instant now = Instant.now();
            Jwt.Builder builder = Jwt.withTokenValue("test-token")
                    .header("alg", "none")
                    .issuedAt(now)
                    .expiresAt(now.plusSeconds(60));
            jwtCustomizer.accept(builder);
            Jwt jwt = builder.build();

            String tenantId = jwt.getClaimAsString("tid");
            SecurityUser principal = new SecurityUser(
                    Long.parseLong(jwt.getSubject()),
                    tenantId == null || tenantId.isBlank() ? null : Long.valueOf(tenantId),
                    "test-user",
                    true,
                    null,
                    Arrays.asList(authorities));
            return SecurityMockMvcRequestPostProcessors.user(principal).postProcessRequest(request);
        };
    }
}
