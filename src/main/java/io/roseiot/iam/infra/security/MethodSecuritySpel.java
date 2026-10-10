package io.roseiot.iam.infra.security;

import io.roseiot.iam.domain.ActionCode;

/** Compile-time {@link org.springframework.security.access.prepost.PreAuthorize} SpEL fragments. */
public final class MethodSecuritySpel {

    public static final String TENANT_CREATE =
            "hasAuthority('" + ActionCode.TENANT_CREATE + "')";

    private static final String TENANT_STATUS_PERMISSION =
            "(#request.disabled == null or (#request.disabled ? hasAuthority('"
                    + ActionCode.TENANT_DISABLE
                    + "') : hasAuthority('"
                    + ActionCode.TENANT_ENABLE
                    + "')))";

    private static final String TENANT_NAME_PERMISSION =
            "(#request.name == null or #request.name.isBlank() or hasAuthority('"
                    + ActionCode.TENANT_CREATE
                    + "'))";

    public static final String PATCH_TENANT =
            TENANT_STATUS_PERMISSION + " and " + TENANT_NAME_PERMISSION;

    public static final String TENANT_USER_MANAGE =
            "hasAuthority('" + ActionCode.TENANT_USER_MANAGE + "')";

    public static final String TENANT_USER_INVITE =
            "hasAuthority('" + ActionCode.TENANT_USER_INVITE + "')";

    public static final String TENANT_INVITE_OR_MANAGE = "hasAnyAuthority('"
            + ActionCode.TENANT_USER_INVITE
            + "', '"
            + ActionCode.TENANT_USER_MANAGE
            + "')";

    private MethodSecuritySpel() {}
}
