package io.roseiot.api;

/**
 * Stable JSON error body for {@code /api/**} REST responses. Success responses use domain DTOs
 * directly (HTTP status indicates outcome); only errors use this shape.
 */
public record ApiErrorResponse(String code, String message) {
}
