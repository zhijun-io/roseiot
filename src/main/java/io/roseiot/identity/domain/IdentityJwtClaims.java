package io.roseiot.identity.domain;

public final class IdentityJwtClaims {

    public static final String CONTEXT = "ctx";
    public static final String AUTHORIZATION_VERSION = "azv";
    public static final String CONTEXT_SYSTEM = "system";
    public static final String CONTEXT_TENANT = "tenant";

    public static final String PERMISSION_PROFILE_READ = "user.profile.read";
    public static final String PERMISSION_PROFILE_PASSWORD_WRITE = "user.password.write";

    private IdentityJwtClaims() {}
}
