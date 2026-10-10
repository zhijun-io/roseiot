package io.roseiot.shared.domain;

/** Domain failure identified by {@link ApiErrorCode}; must not carry HTTP semantics. */
public class BusinessException extends RuntimeException {

    private final ApiErrorCode errorCode;
    private final String detail;

    public BusinessException(ApiErrorCode errorCode) {
        this(errorCode, null);
    }

    public BusinessException(ApiErrorCode errorCode, String detail) {
        super(detail != null ? detail : errorCode.code());
        this.errorCode = errorCode;
        this.detail = detail;
    }

    public ApiErrorCode errorCode() {
        return errorCode;
    }

    /** JSON {@code message}; {@link ApiErrorCode#VALIDATION_ERROR} may carry a safe rule detail. */
    public String apiMessage() {
        if (errorCode == ApiErrorCode.VALIDATION_ERROR) {
            return detail != null ? detail : errorCode.code();
        }
        return errorCode.code();
    }
}
