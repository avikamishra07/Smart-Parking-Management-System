package com.rcoem.parking.exception;

/**
 * Slot already occupied, vehicle already parked, booking already checked out / paid, or payment before
 * checkout. Mapped to HTTP 409 by {@link GlobalExceptionHandler}.
 */
public class BookingConflictException extends RuntimeException {
    public BookingConflictException(String message) {
        super(message);
    }
}
