package com.rcoem.parking.dto;

import com.rcoem.parking.domain.BookingStatus;
import com.rcoem.parking.domain.VehicleBooking;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;

/**
 * Fare for a booking as of {@code asOf}: the current time for an ACTIVE booking (so the fare can
 * still grow), or the exit time for a COMPLETED one, in which case {@code finalFare} is true.
 */
public record FareResponse(
        Long bookingId,
        String slotNumber,
        String vehicleNumber,
        LocalDateTime entryTime,
        LocalDateTime asOf,
        long durationMinutes,
        long billedBlocks,
        BigDecimal fare,
        BookingStatus bookingStatus,
        boolean finalFare) {

    public static FareResponse from(VehicleBooking b, LocalDateTime asOf, long billedBlocks, BigDecimal fare) {
        long minutes = Duration.between(b.getEntryTime(), asOf).toMinutes();
        return new FareResponse(b.getId(), b.getSlot().getSlotNumber(), b.getVehicleNumber(),
                b.getEntryTime(), asOf, minutes, billedBlocks, fare, b.getBookingStatus(),
                b.getBookingStatus() == BookingStatus.COMPLETED);
    }
}
