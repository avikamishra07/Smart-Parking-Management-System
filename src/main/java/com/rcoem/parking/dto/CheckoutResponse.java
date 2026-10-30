package com.rcoem.parking.dto;

import com.rcoem.parking.domain.BookingStatus;
import com.rcoem.parking.domain.PaymentStatus;
import com.rcoem.parking.domain.VehicleBooking;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;

/** Bill returned at checkout. */
public record CheckoutResponse(
        Long bookingId,
        Long slotId,
        String slotNumber,
        String vehicleNumber,
        LocalDateTime entryTime,
        LocalDateTime exitTime,
        long durationMinutes,
        long billedBlocks,
        BigDecimal fee,
        BookingStatus bookingStatus,
        PaymentStatus paymentStatus) {

    public static CheckoutResponse from(VehicleBooking b, long billedBlocks) {
        long minutes = Duration.between(b.getEntryTime(), b.getExitTime()).toMinutes();
        return new CheckoutResponse(b.getId(), b.getSlot().getId(), b.getSlot().getSlotNumber(),
                b.getVehicleNumber(), b.getEntryTime(), b.getExitTime(), minutes, billedBlocks,
                b.getFee(), b.getBookingStatus(), b.getPaymentStatus());
    }
}
