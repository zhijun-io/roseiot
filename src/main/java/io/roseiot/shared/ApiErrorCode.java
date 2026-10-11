package io.roseiot.shared;

/**
 * Stable error identifiers ({@link #code()}) for JSON and i18n keys. HTTP status is chosen only in adapters
 * such as API and security exception handlers.
 */
public enum ApiErrorCode {
    /** Spring Security {@link org.springframework.security.authentication.BadCredentialsException}. */
    INVALID_CREDENTIALS("invalid_credentials"),
    INVALID_RESET_TOKEN("invalid_reset_token"),
    VALIDATION_ERROR("validation_error"),
    TENANT_NOT_FOUND("tenant_not_found"),
    TENANT_USER_NOT_FOUND("tenant_user_not_found"),
    EMAIL_ALREADY_EXISTS("email_already_exists"),
    ROLE_NOT_FOUND("role_not_found"),
    ROLE_IN_USE("role_in_use"),
    SYSTEM_SETTING_NOT_FOUND("system_setting_not_found"),
    SYSTEM_SETTING_EXISTS("system_setting_exists"),
    INVALID_ACTION("invalid_action"),
    LAST_TENANT_ADMIN("last_tenant_admin"),
    REQUEST_TOO_LARGE("request_too_large"),
    /** Used with {@link io.roseiot.shared.ratelimit.RateLimitExceededException} (HTTP 429), not {@link BusinessException}. */
    RATE_LIMITED("rate_limited"),
    RATE_LIMIT_CAPACITY_EXHAUSTED("rate_limit_capacity_exhausted");

    private final String code;

    ApiErrorCode(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }
}
