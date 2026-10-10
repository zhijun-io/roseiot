package io.roseiot.shared.domain;

/**
 * Stable error identifiers ({@link #code()}) for JSON and i18n keys. HTTP status is chosen only in adapters
 * ({@link io.roseiot.shared.api.ApiExceptionHandler},
 * {@link io.roseiot.iam.infra.security.SecurityExceptionHandler}, etc.).
 */
public enum ApiErrorCode {
    /** Spring Security {@link org.springframework.security.authentication.BadCredentialsException}. */
    INVALID_CREDENTIALS("invalid_credentials"),
    /** {@link io.roseiot.iam.infra.security.InvalidRefreshTokenAuthenticationException}. */
    INVALID_REFRESH_TOKEN("invalid_refresh_token"),
    INVALID_RESET_TOKEN("invalid_reset_token"),
    INVALID_INVITATION_TOKEN("invalid_invitation_token"),
    NOT_IMPLEMENTED("not_implemented"),
    VALIDATION_ERROR("validation_error"),
    TENANT_NOT_FOUND("tenant_not_found"),
    TENANT_SLUG_CONFLICT("tenant_slug_conflict"),
    INVITATION_EMAIL_CONFLICT("invitation_email_conflict"),
    INVITATION_NOT_FOUND("invitation_not_found"),
    TENANT_USER_NOT_FOUND("tenant_user_not_found"),
    ROLE_NOT_FOUND("role_not_found"),
    ROLE_CODE_CONFLICT("role_code_conflict"),
    ROLE_IN_USE("role_in_use"),
    ROLE_PROTECTED("role_protected"),
    INVALID_ACTION("invalid_action"),
    LAST_TENANT_ADMIN("last_tenant_admin"),
    /** Used with {@link io.roseiot.shared.ratelimit.RateLimitExceededException} (HTTP 429), not {@link BusinessException}. */
    RATE_LIMITED("rate_limited");

    private final String code;

    ApiErrorCode(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }
}
