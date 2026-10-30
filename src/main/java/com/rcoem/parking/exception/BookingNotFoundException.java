package com.rcoem.parking.exception;

/** Unknown booking. Mapped to HTTP 404 by {@link GlobalExceptionHandler}. */
public class BookingNotFoundException extends RuntimeException {
    public BookingNotFoundException(Long id) {
        super("Booking not found with id " + id);
    }
}
