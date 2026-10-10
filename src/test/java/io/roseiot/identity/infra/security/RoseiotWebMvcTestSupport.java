package io.roseiot.identity.infra.security;

import io.roseiot.identity.domain.IdentityJwtClaims;
import io.roseiot.identity.application.RoleService;
import io.roseiot.identity.persistence.User;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Stream;

public final class RoseiotWebMvcTestSupport {

    private RoseiotWebMvcTestSupport() {}

    public static SimpleGrantedAuthority roleSystemAdmin() {
        return new SimpleGrantedAuthority("ROLE_" + RoleService.SYSTEM_ADMIN);
    }

    public static SimpleGrantedAuthority roleTenantAdmin() {
        return new SimpleGrantedAuthority("ROLE_" + RoleService.TENANT_ADMIN);
    }

    public static SimpleGrantedAuthority[] systemAdminAuthorities() {
        return Stream.of(
                        IdentityJwtClaims.PERMISSION_PROFILE_READ,
                        IdentityJwtClaims.PERMISSION_PROFILE_PASSWORD_WRITE,
                        IdentityJwtClaims.PERMISSION_TENANT_CREATE,
                        IdentityJwtClaims.PERMISSION_TENANT_DISABLE,
                        IdentityJwtClaims.PERMISSION_TENANT_ENABLE)
                .map(SimpleGrantedAuthority::new)
                .toArray(SimpleGrantedAuthority[]::new);
    }

    public static SimpleGrantedAuthority roleTenantUser() {
        return new SimpleGrantedAuthority("ROLE_" + RoleService.TENANT_USER);
    }

    public static RequestPostProcessor jwtWithTenantUser(
            Consumer<org.springframework.security.oauth2.jwt.Jwt.Builder> jwtCustomizer) {
        SimpleGrantedAuthority[] permissions = Stream.of(
                        IdentityJwtClaims.PERMISSION_PROFILE_READ,
                        IdentityJwtClaims.PERMISSION_PROFILE_PASSWORD_WRITE)
                .map(SimpleGrantedAuthority::new)
                .toArray(SimpleGrantedAuthority[]::new);
        SimpleGrantedAuthority[] withRole = new SimpleGrantedAuthority[permissions.length + 1];
        System.arraycopy(permissions, 0, withRole, 0, permissions.length);
        withRole[permissions.length] = roleTenantUser();
        return jwtWithAuthorities(jwtCustomizer, withRole);
    }

    public static SimpleGrantedAuthority[] tenantAdminAuthorities() {
        SimpleGrantedAuthority[] permissions = Stream.of(
                        IdentityJwtClaims.PERMISSION_PROFILE_READ,
                        IdentityJwtClaims.PERMISSION_PROFILE_PASSWORD_WRITE,
                        IdentityJwtClaims.PERMISSION_TENANT_USER_INVITE,
                        IdentityJwtClaims.PERMISSION_TENANT_USER_MANAGE)
                .map(SimpleGrantedAuthority::new)
                .toArray(SimpleGrantedAuthority[]::new);
        SimpleGrantedAuthority[] withRole = new SimpleGrantedAuthority[permissions.length + 1];
        System.arraycopy(permissions, 0, withRole, 0, permissions.length);
        withRole[permissions.length] = roleTenantAdmin();
        return withRole;
    }

    /** @deprecated use {@link #jwtWithSystemAdmin} */
    public static SimpleGrantedAuthority[] allStageOneAuthorities() {
        SimpleGrantedAuthority[] base = systemAdminAuthorities();
        SimpleGrantedAuthority[] withRole = new SimpleGrantedAuthority[base.length + 1];
        System.arraycopy(base, 0, withRole, 0, base.length);
        withRole[base.length] = roleSystemAdmin();
        return withRole;
    }

    public static RequestPostProcessor jwtWithSystemAdmin(
            Consumer<org.springframework.security.oauth2.jwt.Jwt.Builder> jwtCustomizer) {
        SimpleGrantedAuthority[] authorities = systemAdminAuthorities();
        SimpleGrantedAuthority[] withRole = new SimpleGrantedAuthority[authorities.length + 1];
        System.arraycopy(authorities, 0, withRole, 0, authorities.length);
        withRole[authorities.length] = roleSystemAdmin();
        return jwtWithAuthorities(jwtCustomizer, withRole);
    }

    public static RequestPostProcessor jwtWithTenantAdmin(
            Consumer<org.springframework.security.oauth2.jwt.Jwt.Builder> jwtCustomizer) {
        return jwtWithAuthorities(jwtCustomizer, tenantAdminAuthorities());
    }

    public static RequestPostProcessor jwtWithAllPermissions(
            Consumer<org.springframework.security.oauth2.jwt.Jwt.Builder> jwtCustomizer) {
        return jwtWithSystemAdmin(jwtCustomizer);
    }

    public static RequestPostProcessor jwtWithAuthorities(
            Consumer<org.springframework.security.oauth2.jwt.Jwt.Builder> jwtCustomizer,
            SimpleGrantedAuthority... authorities) {
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
            user.setAuthorizationVersion(Math.toIntExact(
                    ((Number) jwt.getClaim(IdentityJwtClaims.AUTHORIZATION_VERSION)).longValue()));
            String tenantId = jwt.getClaimAsString("tid");
            if (tenantId != null && !tenantId.isBlank()) {
                user.setTenantId(UUID.fromString(tenantId));
            }
            SecurityUser principal = new SecurityUser(user, Arrays.asList(authorities));
            return SecurityMockMvcRequestPostProcessors.user(principal).postProcessRequest(request);
        };
    }
}
