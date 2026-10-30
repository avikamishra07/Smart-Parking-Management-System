package com.rcoem.parking.interfaces;

import com.rcoem.parking.dto.BookingResponse;
import com.rcoem.parking.dto.CheckoutResponse;
import com.rcoem.parking.dto.EntryRequest;
import com.rcoem.parking.dto.FareResponse;
import com.rcoem.parking.services.BookingService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/bookings")
public class BookingController {

    private final BookingService bookingService;

    public BookingController(BookingService bookingService) {
        this.bookingService = bookingService;
    }

    @PostMapping("/entry")
    @ResponseStatus(HttpStatus.CREATED)
    public BookingResponse enter(@Valid @RequestBody EntryRequest request) {
        return bookingService.enterVehicle(request);
    }

    /** Checkout + billing: completes the booking, returns the bill and frees the slot. */
    @PostMapping("/{id}/checkout")
    public CheckoutResponse checkout(@PathVariable @Positive(message = "Booking id must be positive") Long id) {
        return bookingService.checkout(id);
    }

    /** Payment status update: PENDING -> PAID for a checked-out booking. */
    @PostMapping("/{id}/pay")
    public BookingResponse pay(@PathVariable @Positive(message = "Booking id must be positive") Long id) {
        return bookingService.markPaid(id);
    }

    /** Live fare for a parked vehicle (read-only); final fee once the booking is checked out. */
    @GetMapping("/{id}/fare")
    public FareResponse getFare(@PathVariable @Positive(message = "Booking id must be positive") Long id) {
        return bookingService.getLiveFare(id);
    }

    @GetMapping("/active")
    public List<BookingResponse> getActiveBookings() {
        return bookingService.getActiveBookings();
    }
}
