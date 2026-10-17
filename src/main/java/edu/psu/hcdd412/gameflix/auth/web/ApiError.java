package edu.psu.hcdd412.gameflix.auth.web;

import org.springframework.http.HttpStatus;

import java.util.Map;
import java.util.UUID;

/**
 * Error body from the Module 3 API conventions: { status, message, correlationId }.
 * fieldErrors is only filled for 400 validation failures.
 */
public record ApiError(int status, String message, String correlationId, Map<String, String> fieldErrors) {

    public static ApiError of(HttpStatus status, String message) {
        return new ApiError(status.value(), message, UUID.randomUUID().toString(), null);
    }

    public static ApiError validation(Map<String, String> fieldErrors) {
        return new ApiError(HttpStatus.BAD_REQUEST.value(), "Validation failed",
                UUID.randomUUID().toString(), fieldErrors);
    }
}
