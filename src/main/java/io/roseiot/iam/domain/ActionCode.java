package io.roseiot.iam.domain;

import java.util.Set;

/** Stable codes for ACTION resources used by role assignment and API authorization. */
public final class ActionCode {

    public static final String TENANT_USER_MANAGE = "tenant.user.manage";
    public static final String TENANT_USER_INVITE = "tenant.user.invite";
    public static final String TENANT_ROLE_MANAGE = "tenant.role.manage";
    public static final String TENANT_CREATE = "tenant.create";
    public static final String TENANT_DISABLE = "tenant.disable";
    public static final String TENANT_ENABLE = "tenant.enable";
    public static final String DEVICE_READ = "device.read";
    public static final String DEVICE_COMMAND_ISSUE = "device.command.issue";
    public static final String DEVICE_CREDENTIAL_READ = "device.credential.read";
    public static final String ALARM_ACK = "alarm.ack";

    public static final Set<String> PLATFORM_ONLY = Set.of(TENANT_CREATE, TENANT_DISABLE, TENANT_ENABLE);
    public static final String PLATFORM_ONLY_SQL_CODES = "'" + TENANT_CREATE + "', '" + TENANT_DISABLE
            + "', '" + TENANT_ENABLE + "'";

    private ActionCode() {}
}
