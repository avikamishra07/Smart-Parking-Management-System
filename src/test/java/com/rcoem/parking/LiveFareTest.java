package com.rcoem.parking;

import com.rcoem.parking.domain.*;
import com.rcoem.parking.dto.FareResponse;
import com.rcoem.parking.exception.BookingNotFoundException;
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

/** Phase 8: live fare. */
class LiveFareTest {

    private static final Instant NOW = Instant.parse("2026-10-30T04:30:00Z"); // 04:30 local (UTC clock)
    private final ParkingSlotRepository slotRepo = mock(ParkingSlotRepository.class);
    private final VehicleBookingRepository bookingRepo = mock(VehicleBookingRepository.class);
    private BookingService service;
    private ParkingSlot slot;

    @BeforeEach
    void setUp() {
        service = new BookingService(slotRepo, bookingRepo, Clock.fixed(NOW, ZoneOffset.UTC),
                new FeeCalculationService(3, new BigDecimal("50")));
        slot = ParkingSlot.builder().id(1L).slotNumber("A-01").status(SlotStatus.OCCUPIED).version(0L).build();
    }

    private VehicleBooking active(LocalDateTime entry) {
        VehicleBooking b = VehicleBooking.builder().id(7L).slot(slot).vehicleNumber("MH31AB1234")
                .entryTime(entry).build();
        when(bookingRepo.findById(7L)).thenReturn(Optional.of(b));
        return b;
    }

    @Test
    void activeBookingFareIsCalculatedUpToNow() {
        active(LocalDateTime.of(2026, 10, 30, 1, 0)); // 3h30m -> 2 blocks
        FareResponse r = service.getLiveFare(7L);
        assertEquals(LocalDateTime.of(2026, 10, 30, 4, 30), r.asOf());
        assertEquals(210, r.durationMinutes());
        assertEquals(2, r.billedBlocks());
        assertEquals(new BigDecimal("100.00"), r.fare());
        assertEquals(BookingStatus.ACTIVE, r.bookingStatus());
        assertFalse(r.finalFare());
    }

    @Test
    void liveFareDoesNotChangeAnything() {
        VehicleBooking b = active(LocalDateTime.of(2026, 10, 30, 1, 0));
        service.getLiveFare(7L);
        assertEquals(BookingStatus.ACTIVE, b.getBookingStatus());
        assertNull(b.getExitTime());
        assertNull(b.getFee());
        assertEquals(SlotStatus.OCCUPIED, slot.getStatus());
        verify(bookingRepo, never()).saveAndFlush(any());
        verify(bookingRepo, never()).save(any());
        verify(bookingRepo, never()).findByIdForUpdate(any());
    }

    @Test
    void justStartedBookingCostsNothing() {
        active(LocalDateTime.of(2026, 10, 30, 4, 30));
        FareResponse r = service.getLiveFare(7L);
        assertEquals(0, r.billedBlocks());
        assertEquals(new BigDecimal("0.00"), r.fare());
    }

    @Test
    void fareStepsUpWhenANewBlockStarts() {
        active(LocalDateTime.of(2026, 10, 30, 1, 29, 59)); // 3h00m01s -> 2 blocks
        assertEquals(new BigDecimal("100.00"), service.getLiveFare(7L).fare());
        active(LocalDateTime.of(2026, 10, 30, 1, 30));     // exactly 3h -> 1 block
        assertEquals(new BigDecimal("50.00"), service.getLiveFare(7L).fare());
    }

    @Test
    void completedBookingReturnsStoredFinalFee() {
        VehicleBooking b = VehicleBooking.builder().id(7L).slot(slot).vehicleNumber("MH31AB1234")
                .entryTime(LocalDateTime.of(2026, 10, 30, 0, 0))
                .exitTime(LocalDateTime.of(2026, 10, 30, 2, 0))
                .fee(new BigDecimal("50.00")).bookingStatus(BookingStatus.COMPLETED).build();
        when(bookingRepo.findById(7L)).thenReturn(Optional.of(b));

        FareResponse r = service.getLiveFare(7L);
        assertTrue(r.finalFare());
        assertEquals(LocalDateTime.of(2026, 10, 30, 2, 0), r.asOf()); // exit time, not "now"
        assertEquals(new BigDecimal("50.00"), r.fare());
        assertEquals(1, r.billedBlocks());
    }

    @Test
    void unknownBookingIsNotFound() {
        when(bookingRepo.findById(99L)).thenReturn(Optional.empty());
        assertThrows(BookingNotFoundException.class, () -> service.getLiveFare(99L));
    }
}
