package io.roseiot.auth;

final class InvalidCredentialsException extends RuntimeException {}

final class InvalidResetTokenException extends RuntimeException {}

final class InvalidPasswordException extends RuntimeException {
    InvalidPasswordException(String message) {
        super(message);
    }
}

final class RateLimitedException extends RuntimeException {}
