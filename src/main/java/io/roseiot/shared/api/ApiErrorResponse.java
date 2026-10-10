package io.roseiot.shared.api;

/**
 * Stable JSON error body for REST responses. Success responses use domain DTOs
 * directly (HTTP status indicates outcome); only errors use this shape.
 */
public record ApiErrorResponse(String code, String message) {
}
