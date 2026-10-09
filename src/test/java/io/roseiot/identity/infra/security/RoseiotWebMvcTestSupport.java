package io.roseiot.identity.infra.security;

import io.roseiot.identity.domain.IdentityJwtClaims;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;

import java.util.function.Consumer;
import java.util.stream.Stream;

public final class RoseiotWebMvcTestSupport {

    private RoseiotWebMvcTestSupport() {}

    public static SimpleGrantedAuthority roleSystemAdmin() {
        return new SimpleGrantedAuthority(RoseiotSecurityAuthorities.ROLE_PREFIX + "SystemAdmin");
    }

    public static SimpleGrantedAuthority roleTenantAdmin() {
        return new SimpleGrantedAuthority(RoseiotSecurityAuthorities.ROLE_PREFIX + "TenantAdmin");
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
        return new SimpleGrantedAuthority(RoseiotSecurityAuthorities.ROLE_PREFIX + "TenantUser");
    }

    public static SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor jwtWithTenantUser(
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

    public static SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor jwtWithSystemAdmin(
            Consumer<org.springframework.security.oauth2.jwt.Jwt.Builder> jwtCustomizer) {
        SimpleGrantedAuthority[] authorities = systemAdminAuthorities();
        SimpleGrantedAuthority[] withRole = new SimpleGrantedAuthority[authorities.length + 1];
        System.arraycopy(authorities, 0, withRole, 0, authorities.length);
        withRole[authorities.length] = roleSystemAdmin();
        return jwtWithAuthorities(jwtCustomizer, withRole);
    }

    public static SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor jwtWithTenantAdmin(
            Consumer<org.springframework.security.oauth2.jwt.Jwt.Builder> jwtCustomizer) {
        return jwtWithAuthorities(jwtCustomizer, tenantAdminAuthorities());
    }

    public static SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor jwtWithAllPermissions(
            Consumer<org.springframework.security.oauth2.jwt.Jwt.Builder> jwtCustomizer) {
        return jwtWithSystemAdmin(jwtCustomizer);
    }

    public static SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor jwtWithAuthorities(
            Consumer<org.springframework.security.oauth2.jwt.Jwt.Builder> jwtCustomizer,
            SimpleGrantedAuthority... authorities) {
        return SecurityMockMvcRequestPostProcessors.jwt()
                .jwt(jwtCustomizer)
                .authorities(authorities);
    }
}
