package io.roseiot.iam.domain;

/** Which tenants may assign an ACTION to their roles. */
public enum ResourceScope {
    PLATFORM,
    TENANT,
    BOTH
}
