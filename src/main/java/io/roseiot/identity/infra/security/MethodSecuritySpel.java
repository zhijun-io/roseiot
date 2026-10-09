package io.roseiot.identity.infra.security;

import io.roseiot.identity.domain.IdentityJwtClaims;

/** Compile-time {@link org.springframework.security.access.prepost.PreAuthorize} SpEL fragments. */
public final class MethodSecuritySpel {

    private static final String USER_UTILS = "io.roseiot.identity.infra.security.SecurityUserUtils";

    public static final String IS_SYSTEM_USER =
            "T(" + USER_UTILS + ").from(authentication).isSystemUser()";

    public static final String IS_TENANT_USER =
            "T(" + USER_UTILS + ").from(authentication).isTenantUser()";

    public static final String PROFILE_READ =
            "hasAuthority('" + IdentityJwtClaims.PERMISSION_PROFILE_READ + "')";

    public static final String PROFILE_PASSWORD_WRITE =
            "hasAuthority('" + IdentityJwtClaims.PERMISSION_PROFILE_PASSWORD_WRITE + "')";

    public static final String TENANT_CREATE =
            IS_SYSTEM_USER + " and hasAuthority('" + IdentityJwtClaims.PERMISSION_TENANT_CREATE + "')";

    public static final String PATCH_TENANT = IS_SYSTEM_USER
            + " and (#request.disabled == null or (#request.disabled ? hasAuthority('"
            + IdentityJwtClaims.PERMISSION_TENANT_DISABLE
            + "') : hasAuthority('"
            + IdentityJwtClaims.PERMISSION_TENANT_ENABLE
            + "'))) and (#request.name == null or #request.name.isBlank() or hasAuthority('"
            + IdentityJwtClaims.PERMISSION_TENANT_CREATE
            + "'))";

    public static final String TENANT_USER_MANAGE =
            IS_TENANT_USER + " and hasAuthority('" + IdentityJwtClaims.PERMISSION_TENANT_USER_MANAGE + "')";

    public static final String TENANT_USER_INVITE =
            IS_TENANT_USER + " and hasAuthority('" + IdentityJwtClaims.PERMISSION_TENANT_USER_INVITE + "')";

    public static final String TENANT_INVITE_OR_MANAGE = IS_TENANT_USER
            + " and hasAnyAuthority('"
            + IdentityJwtClaims.PERMISSION_TENANT_USER_INVITE
            + "', '"
            + IdentityJwtClaims.PERMISSION_TENANT_USER_MANAGE
            + "')";

    private MethodSecuritySpel() {}
}
