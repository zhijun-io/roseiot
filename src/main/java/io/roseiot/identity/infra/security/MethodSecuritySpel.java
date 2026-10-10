package io.roseiot.identity.infra.security;

import io.roseiot.identity.domain.IdentityJwtClaims;

/** Compile-time {@link org.springframework.security.access.prepost.PreAuthorize} SpEL fragments. */
public final class MethodSecuritySpel {

    private static final String USER_FROM_AUTHENTICATION =
            "T(io.roseiot.identity.infra.security.SecurityUserUtils).from(authentication)";

    public static final String IS_SYSTEM_USER =
            USER_FROM_AUTHENTICATION + ".isSystemUser()";

    public static final String IS_TENANT_USER =
            USER_FROM_AUTHENTICATION + ".isTenantUser()";

    public static final String PROFILE_READ =
            "hasAuthority('" + IdentityJwtClaims.PERMISSION_PROFILE_READ + "')";

    public static final String PROFILE_PASSWORD_WRITE =
            "hasAuthority('" + IdentityJwtClaims.PERMISSION_PROFILE_PASSWORD_WRITE + "')";

    public static final String TENANT_CREATE =
            IS_SYSTEM_USER + " and hasAuthority('" + IdentityJwtClaims.PERMISSION_TENANT_CREATE + "')";

    private static final String TENANT_STATUS_PERMISSION =
            "(#request.disabled == null or (#request.disabled ? hasAuthority('"
                    + IdentityJwtClaims.PERMISSION_TENANT_DISABLE
                    + "') : hasAuthority('"
                    + IdentityJwtClaims.PERMISSION_TENANT_ENABLE
                    + "')))";

    private static final String TENANT_NAME_PERMISSION =
            "(#request.name == null or #request.name.isBlank() or hasAuthority('"
                    + IdentityJwtClaims.PERMISSION_TENANT_CREATE
                    + "'))";

    public static final String PATCH_TENANT =
            IS_SYSTEM_USER + " and " + TENANT_STATUS_PERMISSION + " and " + TENANT_NAME_PERMISSION;

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
