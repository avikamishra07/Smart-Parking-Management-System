package com.rcoem.parking.dto;

import com.rcoem.parking.domain.BookingStatus;
import com.rcoem.parking.domain.PaymentStatus;
import com.rcoem.parking.domain.VehicleBooking;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record BookingResponse(
        Long id,
        Long slotId,
        String slotNumber,
        String vehicleNumber,
        LocalDateTime entryTime,
        LocalDateTime exitTime,
        BigDecimal fee,
        BookingStatus bookingStatus,
        PaymentStatus paymentStatus) {

    public static BookingResponse from(VehicleBooking b) {
        return new BookingResponse(b.getId(), b.getSlot().getId(), b.getSlot().getSlotNumber(),
                b.getVehicleNumber(), b.getEntryTime(), b.getExitTime(), b.getFee(),
                b.getBookingStatus(), b.getPaymentStatus());
    }
}
