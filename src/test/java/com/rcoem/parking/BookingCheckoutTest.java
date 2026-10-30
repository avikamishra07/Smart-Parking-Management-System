package com.rcoem.parking;

import com.rcoem.parking.domain.*;
import com.rcoem.parking.dto.BookingResponse;
import com.rcoem.parking.dto.CheckoutResponse;
import com.rcoem.parking.exception.BookingConflictException;
import com.rcoem.parking.exception.BookingNotFoundException;
import com.rcoem.parking.exception.InvalidParkingTimeException;
import com.rcoem.parking.services.BookingService;
import com.rcoem.parking.services.FeeCalculationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.*;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Phase 7: checkout, billing, payment status, slot release, duplicate-checkout protection. */
class BookingCheckoutTest {

    // clock "now" = 2026-10-30 04:30 (UTC)
    private static final Instant NOW = Instant.parse("2026-10-30T04:30:00Z");
    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
    private final ParkingSlotRepository slotRepo = mock(ParkingSlotRepository.class);
    private final VehicleBookingRepository bookingRepo = mock(VehicleBookingRepository.class);
    private BookingService service;
    private ParkingSlot slot;

    @BeforeEach
    void setUp() {
        service = new BookingService(slotRepo, bookingRepo, clock,
                new FeeCalculationService(3, new BigDecimal("50")));
        slot = ParkingSlot.builder().id(1L).slotNumber("A-01").status(SlotStatus.OCCUPIED).version(0L).build();
        when(slotRepo.findByIdForUpdate(1L)).thenReturn(Optional.of(slot));
    }

    private VehicleBooking activeBooking(LocalDateTime entry) {
        VehicleBooking b = VehicleBooking.builder().id(7L).slot(slot).vehicleNumber("MH31AB1234")
                .entryTime(entry).build();
        when(bookingRepo.findByIdForUpdate(7L)).thenReturn(Optional.of(b));
        return b;
    }

    private VehicleBooking completedBooking(PaymentStatus payment) {
        VehicleBooking b = VehicleBooking.builder().id(7L).slot(slot).vehicleNumber("MH31AB1234")
                .entryTime(LocalDateTime.of(2026, 10, 30, 1, 0))
                .exitTime(LocalDateTime.of(2026, 10, 30, 4, 30))
                .fee(new BigDecimal("100.00"))
                .bookingStatus(BookingStatus.COMPLETED).paymentStatus(payment).build();
        when(bookingRepo.findByIdForUpdate(7L)).thenReturn(Optional.of(b));
        return b;
    }

    // ---------- checkout ----------

    @Test
    void checkoutCompletesBookingBillsFeeAndReleasesSlot() {
        VehicleBooking b = activeBooking(LocalDateTime.of(2026, 10, 30, 1, 0)); // 3h30m -> 2 blocks

        CheckoutResponse r = service.checkout(7L);

        assertEquals(LocalDateTime.of(2026, 10, 30, 4, 30), r.exitTime());
        assertEquals(210, r.durationMinutes());
        assertEquals(2, r.billedBlocks());
        assertEquals(new BigDecimal("100.00"), r.fee());
        assertEquals(BookingStatus.COMPLETED, r.bookingStatus());
        assertEquals(PaymentStatus.PENDING, r.paymentStatus());
        assertEquals("A-01", r.slotNumber());
        assertEquals(BookingStatus.COMPLETED, b.getBookingStatus());
        assertEquals(SlotStatus.AVAILABLE, slot.getStatus());
        verify(bookingRepo).saveAndFlush(b);
        verify(slotRepo).saveAndFlush(slot);
    }

    @Test
    void checkoutAtExactBlockBoundaryChargesOneBlock() {
        activeBooking(LocalDateTime.of(2026, 10, 30, 1, 30)); // exactly 3h
        CheckoutResponse r = service.checkout(7L);
        assertEquals(1, r.billedBlocks());
        assertEquals(new BigDecimal("50.00"), r.fee());
    }

    @Test
    void checkoutWithZeroDurationCostsNothing() {
        activeBooking(LocalDateTime.of(2026, 10, 30, 4, 30));
        CheckoutResponse r = service.checkout(7L);
        assertEquals(0, r.billedBlocks());
        assertEquals(new BigDecimal("0.00"), r.fee());
        assertEquals(SlotStatus.AVAILABLE, slot.getStatus());
    }

    @Test
    void checkoutOfUnknownBookingIsNotFound() {
        when(bookingRepo.findByIdForUpdate(99L)).thenReturn(Optional.empty());
        assertThrows(BookingNotFoundException.class, () -> service.checkout(99L));
        verify(bookingRepo, never()).saveAndFlush(any());
        verify(slotRepo, never()).saveAndFlush(any());
    }

    @Test
    void secondCheckoutOfSameBookingIsConflictAndChangesNothing() {
        activeBooking(LocalDateTime.of(2026, 10, 30, 1, 0));
        service.checkout(7L);

        // a new vehicle takes the freed slot; a repeated checkout must not release it again
        slot.setStatus(SlotStatus.OCCUPIED);
        assertThrows(BookingConflictException.class, () -> service.checkout(7L));

        assertEquals(SlotStatus.OCCUPIED, slot.getStatus());
        verify(bookingRepo, times(1)).saveAndFlush(any(VehicleBooking.class));
        verify(slotRepo, times(1)).saveAndFlush(any(ParkingSlot.class));
    }

    @Test
    void checkoutOfAlreadyCompletedBookingIsConflict() {
        completedBooking(PaymentStatus.PAID);
        assertThrows(BookingConflictException.class, () -> service.checkout(7L));
        verify(bookingRepo, never()).saveAndFlush(any());
    }

    @Test
    void exitBeforeEntryLeavesBookingActiveAndSlotOccupied() {
        VehicleBooking b = activeBooking(LocalDateTime.of(2026, 10, 30, 5, 0)); // entry after "now"
        assertThrows(InvalidParkingTimeException.class, () -> service.checkout(7L));
        assertEquals(BookingStatus.ACTIVE, b.getBookingStatus());
        assertNull(b.getExitTime());
        assertNull(b.getFee());
        assertEquals(SlotStatus.OCCUPIED, slot.getStatus());
        verify(bookingRepo, never()).saveAndFlush(any());
    }

    // ---------- payment ----------

    @Test
    void markPaidUpdatesPaymentStatus() {
        VehicleBooking b = completedBooking(PaymentStatus.PENDING);
        BookingResponse r = service.markPaid(7L);
        assertEquals(PaymentStatus.PAID, r.paymentStatus());
        assertEquals(PaymentStatus.PAID, b.getPaymentStatus());
        verify(bookingRepo).saveAndFlush(b);
    }

    @Test
    void markPaidTwiceIsConflict() {
        completedBooking(PaymentStatus.PAID);
        assertThrows(BookingConflictException.class, () -> service.markPaid(7L));
        verify(bookingRepo, never()).saveAndFlush(any());
    }

    @Test
    void markPaidBeforeCheckoutIsConflict() {
        VehicleBooking b = activeBooking(LocalDateTime.of(2026, 10, 30, 1, 0));
        assertThrows(BookingConflictException.class, () -> service.markPaid(7L));
        assertEquals(PaymentStatus.PENDING, b.getPaymentStatus());
        verify(bookingRepo, never()).saveAndFlush(any());
    }

    @Test
    void markPaidOfUnknownBookingIsNotFound() {
        when(bookingRepo.findByIdForUpdate(99L)).thenReturn(Optional.empty());
        assertThrows(BookingNotFoundException.class, () -> service.markPaid(99L));
    }
}
