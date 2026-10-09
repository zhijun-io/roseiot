package io.roseiot.identity.domain;

public final class IdentityJwtClaims {

    public static final String CONTEXT = "ctx";
    public static final String AUTHORIZATION_VERSION = "azv";
    public static final String TENANT_ID = "tid";
    public static final String CONTEXT_SYSTEM = "system";
    public static final String CONTEXT_TENANT = "tenant";

    public static final String PERMISSION_PROFILE_READ = "user.profile.read";
    public static final String PERMISSION_PROFILE_PASSWORD_WRITE = "user.password.write";
    public static final String PERMISSION_TENANT_CREATE = "tenant.create";
    public static final String PERMISSION_TENANT_DISABLE = "tenant.disable";
    public static final String PERMISSION_TENANT_ENABLE = "tenant.enable";
    public static final String PERMISSION_TENANT_USER_INVITE = "tenant.user.invite";
    public static final String PERMISSION_TENANT_USER_MANAGE = "tenant.user.manage";

    private IdentityJwtClaims() {}
}
