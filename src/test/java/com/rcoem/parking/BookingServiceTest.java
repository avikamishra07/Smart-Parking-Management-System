package com.rcoem.parking;

import com.rcoem.parking.domain.*;
import com.rcoem.parking.dto.BookingResponse;
import com.rcoem.parking.dto.EntryRequest;
import com.rcoem.parking.exception.BookingConflictException;
import com.rcoem.parking.exception.SlotNotFoundException;
import com.rcoem.parking.services.BookingService;
import com.rcoem.parking.services.FeeCalculationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
import java.time.*;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class BookingServiceTest {

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
        slot = ParkingSlot.builder().id(1L).slotNumber("A-01").status(SlotStatus.AVAILABLE).version(0L).build();
        when(slotRepo.findByIdForUpdate(1L)).thenReturn(Optional.of(slot));
        when(bookingRepo.saveAndFlush(any(VehicleBooking.class))).thenAnswer(i -> i.getArgument(0));
    }

    @Test
    void entryCreatesActiveBookingAndOccupiesSlot() {
        BookingResponse r = service.enterVehicle(new EntryRequest("mh 31-ab 1234", 1L));
        assertEquals("MH31AB1234", r.vehicleNumber());
        assertEquals(BookingStatus.ACTIVE, r.bookingStatus());
        assertEquals(PaymentStatus.PENDING, r.paymentStatus());
        assertEquals(LocalDateTime.of(2026, 10, 30, 4, 30), r.entryTime());
        assertNull(r.exitTime());
        assertNull(r.fee());
        assertEquals(SlotStatus.OCCUPIED, slot.getStatus());
    }

    @Test
    void missingSlotIsNotFound() {
        when(slotRepo.findByIdForUpdate(9L)).thenReturn(Optional.empty());
        assertThrows(SlotNotFoundException.class, () -> service.enterVehicle(new EntryRequest("MH31AB1234", 9L)));
        verify(bookingRepo, never()).saveAndFlush(any());
    }

    @Test
    void occupiedSlotIsConflict() {
        slot.setStatus(SlotStatus.OCCUPIED);
        assertThrows(BookingConflictException.class, () -> service.enterVehicle(new EntryRequest("MH31AB1234", 1L)));
        verify(bookingRepo, never()).saveAndFlush(any());
    }

    @Test
    void vehicleAlreadyParkedIsConflictAndSlotStaysAvailable() {
        when(bookingRepo.existsByVehicleNumberAndBookingStatus("MH31AB1234", BookingStatus.ACTIVE)).thenReturn(true);
        assertThrows(BookingConflictException.class, () -> service.enterVehicle(new EntryRequest("mh31ab1234", 1L)));
        assertEquals(SlotStatus.AVAILABLE, slot.getStatus());
    }

    @Test
    void databaseUniqueViolationBecomesConflict() {
        when(bookingRepo.saveAndFlush(any(VehicleBooking.class))).thenThrow(new DataIntegrityViolationException("dup"));
        assertThrows(BookingConflictException.class, () -> service.enterVehicle(new EntryRequest("MH31AB1234", 1L)));
    }

    @Test
    void activeBookingsAreMapped() {
        VehicleBooking b = VehicleBooking.builder().id(7L).slot(slot).vehicleNumber("MH31AB1234")
                .entryTime(LocalDateTime.of(2026, 10, 30, 1, 0)).build();
        when(bookingRepo.findByBookingStatusWithSlot(BookingStatus.ACTIVE)).thenReturn(List.of(b));
        List<BookingResponse> list = service.getActiveBookings();
        assertEquals(1, list.size());
        assertEquals("A-01", list.get(0).slotNumber());
    }
}
