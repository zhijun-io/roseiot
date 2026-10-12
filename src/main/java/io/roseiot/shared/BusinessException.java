package io.roseiot.shared;

/** Domain failure identified by {@link ApiErrorCode}; must not carry HTTP semantics. */
public class BusinessException extends RuntimeException {

    private final ApiErrorCode errorCode;

    public BusinessException(ApiErrorCode errorCode) {
        this(errorCode, null);
    }

    public BusinessException(ApiErrorCode errorCode, String detail) {
        super(detail != null ? detail : errorCode.code());
        this.errorCode = errorCode;
    }

    public BusinessException(ApiErrorCode errorCode, String detail, Throwable cause) {
        super(detail != null ? detail : errorCode.code(), cause);
        this.errorCode = errorCode;
    }

    public ApiErrorCode errorCode() {
        return errorCode;
    }

}
