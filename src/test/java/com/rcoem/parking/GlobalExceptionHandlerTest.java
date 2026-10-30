package com.rcoem.parking;

import com.rcoem.parking.exception.*;
import com.rcoem.parking.interfaces.BookingController;
import com.rcoem.parking.interfaces.ReportController;
import com.rcoem.parking.interfaces.SlotController;
import com.rcoem.parking.services.BookingService;
import com.rcoem.parking.services.ReportService;
import com.rcoem.parking.services.SlotService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Phase 9: every failure must come back as the standard ApiError JSON with the right status.
 * Real controllers + real GlobalExceptionHandler, mocked services (no database needed).
 */
class GlobalExceptionHandlerTest {

    private final SlotService slotService = mock(SlotService.class);
    private final BookingService bookingService = mock(BookingService.class);
    private final ReportService reportService = mock(ReportService.class);
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        Clock clock = Clock.fixed(Instant.parse("2026-10-30T04:30:00Z"), ZoneOffset.UTC);
        mvc = MockMvcBuilders
                .standaloneSetup(new SlotController(slotService),
                        new BookingController(bookingService),
                        new ReportController(reportService))
                .setControllerAdvice(new GlobalExceptionHandler(clock))
                .setValidator(validator)
                .build();
    }

    /** Asserts the fields every error response must carry. */
    private ResultActions assertStandardError(ResultActions r, int status, String error, String path) throws Exception {
        return r.andExpect(status().is(status))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.status").value(status))
                .andExpect(jsonPath("$.error").value(error))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.path").value(path))
                .andExpect(jsonPath("$.details").isArray());
    }

    // ------------------------------------------------------------ 404

    @Test
    void unknownSlotReturns404() throws Exception {
        when(slotService.getSlotById(99L)).thenThrow(new SlotNotFoundException(99L));
        assertStandardError(mvc.perform(get("/slots/99")), 404, "Not Found", "/slots/99")
                .andExpect(jsonPath("$.message").value("Parking slot not found with id 99"));
    }

    @Test
    void unknownBookingReturns404OnCheckout() throws Exception {
        when(bookingService.checkout(7L)).thenThrow(new BookingNotFoundException(7L));
        assertStandardError(mvc.perform(post("/bookings/7/checkout")), 404, "Not Found", "/bookings/7/checkout")
                .andExpect(jsonPath("$.message").value("Booking not found with id 7"));
    }

    @Test
    void entryForUnknownSlotReturns404() throws Exception {
        when(bookingService.enterVehicle(any())).thenThrow(new SlotNotFoundException(42L));
        assertStandardError(postEntry("{\"vehicleNumber\":\"MH31AB1234\",\"slotId\":42}"),
                404, "Not Found", "/bookings/entry");
    }

    // ------------------------------------------------------------ 409

    @Test
    void occupiedSlotReturns409() throws Exception {
        when(bookingService.enterVehicle(any()))
                .thenThrow(new BookingConflictException("Slot A-01 is already occupied"));
        assertStandardError(postEntry("{\"vehicleNumber\":\"MH31AB1234\",\"slotId\":1}"),
                409, "Conflict", "/bookings/entry")
                .andExpect(jsonPath("$.message").value("Slot A-01 is already occupied"));
    }

    @Test
    void duplicateCheckoutReturns409() throws Exception {
        when(bookingService.checkout(3L))
                .thenThrow(new BookingConflictException("Booking 3 has already been checked out"));
        assertStandardError(mvc.perform(post("/bookings/3/checkout")), 409, "Conflict", "/bookings/3/checkout")
                .andExpect(jsonPath("$.message", containsString("already been checked out")));
    }

    @Test
    void payBeforeCheckoutReturns409() throws Exception {
        when(bookingService.markPaid(4L))
                .thenThrow(new BookingConflictException("Booking 4 must be checked out before payment"));
        assertStandardError(mvc.perform(post("/bookings/4/pay")), 409, "Conflict", "/bookings/4/pay");
    }

    @Test
    void databaseConstraintViolationReturns409WithoutLeakingSql() throws Exception {
        when(bookingService.enterVehicle(any())).thenThrow(new DataIntegrityViolationException(
                "could not execute statement [ERROR: duplicate key value violates unique constraint "
                        + "\"uq_active_booking_per_slot\"] [insert into vehicle_booking ...]"));
        assertStandardError(postEntry("{\"vehicleNumber\":\"MH31AB1234\",\"slotId\":1}"),
                409, "Conflict", "/bookings/entry")
                .andExpect(content().string(not(containsString("uq_active_booking_per_slot"))))
                .andExpect(content().string(not(containsString("insert into"))));
    }

    @Test
    void optimisticLockFailureReturns409() throws Exception {
        when(bookingService.markPaid(5L)).thenThrow(new OptimisticLockingFailureException("stale version 3"));
        assertStandardError(mvc.perform(post("/bookings/5/pay")), 409, "Conflict", "/bookings/5/pay")
                .andExpect(content().string(not(containsString("stale version"))));
    }

    // ------------------------------------------------------------ 400

    @Test
    void invalidEntryBodyReturns400WithFieldDetails() throws Exception {
        assertStandardError(postEntry("{\"vehicleNumber\":\"\"}"), 400, "Bad Request", "/bookings/entry")
                .andExpect(jsonPath("$.message").value("Validation failed"))
                .andExpect(jsonPath("$.details[*].field", hasItems("vehicleNumber", "slotId")));
    }

    @Test
    void badVehicleNumberPatternReturns400() throws Exception {
        assertStandardError(postEntry("{\"vehicleNumber\":\"!!\",\"slotId\":1}"), 400, "Bad Request", "/bookings/entry")
                .andExpect(jsonPath("$.details[0].field").value("vehicleNumber"));
    }

    @Test
    void negativeSlotIdInBodyReturns400() throws Exception {
        assertStandardError(postEntry("{\"vehicleNumber\":\"MH31AB1234\",\"slotId\":-5}"),
                400, "Bad Request", "/bookings/entry")
                .andExpect(jsonPath("$.details[0].field").value("slotId"));
    }

    @Test
    void malformedJsonReturns400WithoutParserDetails() throws Exception {
        assertStandardError(postEntry("{\"vehicleNumber\": "), 400, "Bad Request", "/bookings/entry")
                .andExpect(jsonPath("$.message").value("Malformed or unreadable request body"))
                .andExpect(content().string(not(containsString("Unexpected"))))
                .andExpect(content().string(not(containsString("jackson"))));
    }

    @Test
    void wrongFieldTypeInBodyReturns400() throws Exception {
        assertStandardError(postEntry("{\"vehicleNumber\":\"MH31AB1234\",\"slotId\":\"abc\"}"),
                400, "Bad Request", "/bookings/entry");
    }

    @Test
    void missingBodyReturns400() throws Exception {
        assertStandardError(mvc.perform(post("/bookings/entry").contentType(MediaType.APPLICATION_JSON)),
                400, "Bad Request", "/bookings/entry");
    }

    @Test
    void nonPositivePathIdReturns400() throws Exception {
        assertStandardError(mvc.perform(get("/slots/0")), 400, "Bad Request", "/slots/0")
                .andExpect(jsonPath("$.details[0].message").value("Slot id must be positive"));
    }

    @Test
    void nonNumericPathIdReturns400() throws Exception {
        assertStandardError(mvc.perform(get("/bookings/abc/fare")), 400, "Bad Request", "/bookings/abc/fare")
                .andExpect(jsonPath("$.message").value("Invalid value for parameter 'id'"));
    }

    @Test
    void invalidReportDateReturns400() throws Exception {
        assertStandardError(mvc.perform(get("/reports/revenue").param("date", "30-10-2026")),
                400, "Bad Request", "/reports/revenue")
                .andExpect(jsonPath("$.message").value("Invalid value for parameter 'date'"));
    }

    @Test
    void invalidParkingTimeReturns400() throws Exception {
        when(bookingService.checkout(8L)).thenThrow(new InvalidParkingTimeException("Exit time must not be before entry time"));
        assertStandardError(mvc.perform(post("/bookings/8/checkout")), 400, "Bad Request", "/bookings/8/checkout")
                .andExpect(jsonPath("$.message").value("Exit time must not be before entry time"));
    }

    // ------------------------------------------------------------ other Spring MVC errors keep their status

    @Test
    void wrongHttpMethodReturns405NotAn500() throws Exception {
        assertStandardError(mvc.perform(delete("/slots/1")), 405, "Method Not Allowed", "/slots/1")
                .andExpect(header().exists("Allow"));
    }

    @Test
    void wrongContentTypeReturns415NotAn500() throws Exception {
        assertStandardError(mvc.perform(post("/bookings/entry").contentType(MediaType.TEXT_PLAIN).content("hello")),
                415, "Unsupported Media Type", "/bookings/entry");
    }

    // ------------------------------------------------------------ 500

    @Test
    void unexpectedExceptionReturns500WithGenericMessage() throws Exception {
        when(slotService.getAllSlots()).thenThrow(new IllegalStateException(
                "jdbc:postgresql://127.0.0.1:5432/parkingdb password=root SELECT * FROM parking_slot"));
        assertStandardError(mvc.perform(get("/slots")), 500, "Internal Server Error", "/slots")
                .andExpect(jsonPath("$.message").value("An unexpected error occurred. Please try again later."))
                .andExpect(content().string(not(containsString("jdbc"))))
                .andExpect(content().string(not(containsString("password"))))
                .andExpect(content().string(not(containsString("SELECT"))))
                .andExpect(content().string(not(containsString("IllegalStateException"))))
                .andExpect(content().string(not(containsString("at com.rcoem"))));
    }

    @Test
    void nullPointerReturns500WithoutStackTrace() throws Exception {
        when(reportService.getDailyRevenue(any())).thenThrow(new NullPointerException("boom"));
        assertStandardError(mvc.perform(get("/reports/revenue")), 500, "Internal Server Error", "/reports/revenue")
                .andExpect(content().string(not(containsString("NullPointerException"))))
                .andExpect(content().string(not(containsString("boom"))));
    }

    // ------------------------------------------------------------

    private ResultActions postEntry(String json) throws Exception {
        return mvc.perform(post("/bookings/entry").contentType(MediaType.APPLICATION_JSON).content(json));
    }
}
