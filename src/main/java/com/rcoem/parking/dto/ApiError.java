package com.rcoem.parking.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Standard error body returned for every failed request (400, 404, 405, 409, 415, 500, ...).
 *
 * <pre>
 * {
 *   "timestamp": "2026-10-30T10:15:30",
 *   "status": 404,
 *   "error": "Not Found",
 *   "message": "Parking slot not found with id 99",
 *   "path": "/parking/slots/99",
 *   "details": []
 * }
 * </pre>
 *
 * {@code details} is empty except for validation failures, where it lists one entry per field.
 */
public record ApiError(
        LocalDateTime timestamp,
        int status,
        String error,
        String message,
        String path,
        List<FieldIssue> details) {

    /** One invalid field / parameter and why it was rejected. */
    public record FieldIssue(String field, String message) {
    }
}
