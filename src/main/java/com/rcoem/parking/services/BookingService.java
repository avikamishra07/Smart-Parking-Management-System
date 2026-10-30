package com.rcoem.parking.services;

import com.rcoem.parking.domain.*;
import com.rcoem.parking.dto.BookingResponse;
import com.rcoem.parking.dto.CheckoutResponse;
import com.rcoem.parking.dto.EntryRequest;
import com.rcoem.parking.dto.FareResponse;
import com.rcoem.parking.exception.BookingConflictException;
import com.rcoem.parking.exception.BookingNotFoundException;
import com.rcoem.parking.exception.SlotNotFoundException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

@Service
public class BookingService {

    private final ParkingSlotRepository slotRepository;
    private final VehicleBookingRepository bookingRepository;
    private final Clock clock;
    private final FeeCalculationService feeCalculationService;

    public BookingService(ParkingSlotRepository slotRepository,
                          VehicleBookingRepository bookingRepository,
                          Clock clock,
                          FeeCalculationService feeCalculationService) {
        this.slotRepository = slotRepository;
        this.bookingRepository = bookingRepository;
        this.clock = clock;
        this.feeCalculationService = feeCalculationService;
    }

    /**
     * Vehicle entry. The slot row is locked (SELECT ... FOR UPDATE) so concurrent requests for the
     * same slot are serialized; the loser sees OCCUPIED and gets 409. The partial unique indexes in
     * schema.sql are a second safety net, translated to 409 below.
     */
    @Transactional
    public BookingResponse enterVehicle(EntryRequest request) {
        String vehicleNumber = normalize(request.vehicleNumber());

        ParkingSlot slot = slotRepository.findByIdForUpdate(request.slotId())
                .orElseThrow(() -> new SlotNotFoundException(request.slotId()));

        if (slot.getStatus() == SlotStatus.OCCUPIED) {
            throw new BookingConflictException("Slot " + slot.getSlotNumber() + " is already occupied");
        }
        if (bookingRepository.existsByVehicleNumberAndBookingStatus(vehicleNumber, BookingStatus.ACTIVE)) {
            throw new BookingConflictException("Vehicle " + vehicleNumber + " is already parked");
        }

        VehicleBooking booking = VehicleBooking.builder()
                .slot(slot)
                .vehicleNumber(vehicleNumber)
                .entryTime(LocalDateTime.now(clock))
                .bookingStatus(BookingStatus.ACTIVE)
                .paymentStatus(PaymentStatus.PENDING)
                .build();
        slot.setStatus(SlotStatus.OCCUPIED);

        try {
            booking = bookingRepository.saveAndFlush(booking);
            slotRepository.saveAndFlush(slot);
        } catch (DataIntegrityViolationException e) {
            throw new BookingConflictException("Slot or vehicle already has an active booking");
        }
        return BookingResponse.from(booking);
    }

    @Transactional(readOnly = true)
    public List<BookingResponse> getActiveBookings() {
        return bookingRepository.findByBookingStatusWithSlot(BookingStatus.ACTIVE)
                .stream().map(BookingResponse::from).toList();
    }

    /**
     * Checkout: computes the fee, completes the booking and releases the slot in one transaction.
     * The booking row is locked first (SELECT ... FOR UPDATE), so two simultaneous checkouts of the
     * same booking are serialized and the second one sees COMPLETED and gets 409. The fee is
     * calculated before any state is changed, so an invalid time range leaves everything untouched.
     * Payment stays PENDING until markPaid() is called.
     */
    @Transactional
    public CheckoutResponse checkout(Long bookingId) {
        VehicleBooking booking = bookingRepository.findByIdForUpdate(bookingId)
                .orElseThrow(() -> new BookingNotFoundException(bookingId));

        if (booking.getBookingStatus() == BookingStatus.COMPLETED) {
            throw new BookingConflictException("Booking " + bookingId + " has already been checked out");
        }

        Long slotId = booking.getSlot().getId();
        ParkingSlot slot = slotRepository.findByIdForUpdate(slotId)
                .orElseThrow(() -> new SlotNotFoundException(slotId));

        LocalDateTime exitTime = LocalDateTime.now(clock);
        long blocks = feeCalculationService.calculateBlocks(booking.getEntryTime(), exitTime);
        BigDecimal fee = feeCalculationService.calculateFee(booking.getEntryTime(), exitTime);

        booking.setExitTime(exitTime);
        booking.setFee(fee);
        booking.setBookingStatus(BookingStatus.COMPLETED);
        booking.setPaymentStatus(PaymentStatus.PENDING);
        slot.setStatus(SlotStatus.AVAILABLE);

        bookingRepository.saveAndFlush(booking);
        slotRepository.saveAndFlush(slot);
        return CheckoutResponse.from(booking, blocks);
    }

    /** Marks a checked-out booking as PAID. Not allowed before checkout, and not allowed twice. */
    @Transactional
    public BookingResponse markPaid(Long bookingId) {
        VehicleBooking booking = bookingRepository.findByIdForUpdate(bookingId)
                .orElseThrow(() -> new BookingNotFoundException(bookingId));

        if (booking.getBookingStatus() != BookingStatus.COMPLETED) {
            throw new BookingConflictException("Booking " + bookingId + " must be checked out before payment");
        }
        if (booking.getPaymentStatus() == PaymentStatus.PAID) {
            throw new BookingConflictException("Booking " + bookingId + " is already paid");
        }

        booking.setPaymentStatus(PaymentStatus.PAID);
        bookingRepository.saveAndFlush(booking);
        return BookingResponse.from(booking);
    }

    /**
     * Live fare. For an ACTIVE booking the fare is calculated up to "now" without changing anything
     * (no lock, nothing saved); it keeps growing until checkout. For a COMPLETED booking the stored
     * final fee is returned.
     */
    @Transactional(readOnly = true)
    public FareResponse getLiveFare(Long bookingId) {
        VehicleBooking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new BookingNotFoundException(bookingId));

        boolean completed = booking.getBookingStatus() == BookingStatus.COMPLETED;
        LocalDateTime asOf = completed ? booking.getExitTime() : LocalDateTime.now(clock);
        long blocks = feeCalculationService.calculateBlocks(booking.getEntryTime(), asOf);
        BigDecimal fare = completed ? booking.getFee()
                : feeCalculationService.calculateFee(booking.getEntryTime(), asOf);
        return FareResponse.from(booking, asOf, blocks, fare);
    }

    /** Upper-case and strip spaces/hyphens so "mh 31-ab 1234" and "MH31AB1234" are the same vehicle. */
    static String normalize(String raw) {
        return raw.trim().toUpperCase(Locale.ROOT).replaceAll("[\\s-]", "");
    }
}
