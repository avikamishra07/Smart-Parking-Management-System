package com.rcoem.parking.exception;

/** Unknown parking slot. Mapped to HTTP 404 by {@link GlobalExceptionHandler}. */
public class SlotNotFoundException extends RuntimeException {
    public SlotNotFoundException(Long id) {
        super("Parking slot not found with id " + id);
    }
}
