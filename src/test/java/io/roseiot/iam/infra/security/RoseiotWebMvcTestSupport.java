package io.roseiot.iam.infra.security;

import io.roseiot.iam.domain.ActionCode;
import io.roseiot.iam.infra.security.JwtTokenFactory;
import io.roseiot.iam.persistence.User;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.Instant;
import java.util.Arrays;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Stream;

/** Authorities in WebMvc slices are ACTION codes only; personal endpoints need authentication, not actions. */
public final class RoseiotWebMvcTestSupport {

    private RoseiotWebMvcTestSupport() {}

    public static SimpleGrantedAuthority action(String code) {
        return new SimpleGrantedAuthority(code);
    }

    public static SimpleGrantedAuthority[] platformAdminAuthorities() {
        return Stream.of(ActionCode.TENANT_CREATE,
                        ActionCode.TENANT_DISABLE,
                        ActionCode.TENANT_ENABLE)
                .map(SimpleGrantedAuthority::new)
                .toArray(SimpleGrantedAuthority[]::new);
    }

    public static SimpleGrantedAuthority[] tenantAdminAuthorities() {
        return Stream.of(ActionCode.TENANT_USER_INVITE,
                        ActionCode.TENANT_USER_MANAGE)
                .map(SimpleGrantedAuthority::new)
                .toArray(SimpleGrantedAuthority[]::new);
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

            User user = new User();
            user.setId(UUID.fromString(jwt.getSubject()));
            String tenantId = jwt.getClaimAsString("tid");
            if (tenantId != null && !tenantId.isBlank()) {
                user.setTenantId(UUID.fromString(tenantId));
            }
            SecurityUser principal = new SecurityUser(user, Arrays.asList(authorities));
            return SecurityMockMvcRequestPostProcessors.user(principal).postProcessRequest(request);
        };
    }

}
