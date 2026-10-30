package com.rcoem.parking.exception;

/** Thrown when entry/exit timestamps are missing or exit is before entry. Mapped to HTTP 400 by {@link GlobalExceptionHandler}. */
public class InvalidParkingTimeException extends IllegalArgumentException {
    public InvalidParkingTimeException(String message) {
        super(message);
    }
}
