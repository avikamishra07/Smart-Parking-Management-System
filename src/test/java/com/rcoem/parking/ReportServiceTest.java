package com.rcoem.parking;

import com.rcoem.parking.domain.VehicleBookingRepository;
import com.rcoem.parking.dto.RevenueResponse;
import com.rcoem.parking.services.ReportService;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Phase 8: daily revenue report. */
class ReportServiceTest {

    private final VehicleBookingRepository repo = mock(VehicleBookingRepository.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-10-30T04:30:00Z"), ZoneOffset.UTC);
    private final ReportService service = new ReportService(repo, clock);

    private static final LocalDateTime DAY_START = LocalDateTime.of(2026, 10, 30, 0, 0);
    private static final LocalDateTime NEXT_DAY = LocalDateTime.of(2026, 10, 31, 0, 0);

    @Test
    void revenueForGivenDateUsesHalfOpenDayRange() {
        when(repo.sumRevenueBetween(DAY_START, NEXT_DAY)).thenReturn(new BigDecimal("350.00"));
        when(repo.countRevenueBookingsBetween(DAY_START, NEXT_DAY)).thenReturn(4L);

        RevenueResponse r = service.getDailyRevenue(LocalDate.of(2026, 10, 30));

        assertEquals(LocalDate.of(2026, 10, 30), r.date());
        assertEquals(new BigDecimal("350.00"), r.totalRevenue());
        assertEquals(4, r.paidBookings());
        verify(repo).sumRevenueBetween(DAY_START, NEXT_DAY);
    }

    @Test
    void missingDateDefaultsToTodayFromClock() {
        when(repo.sumRevenueBetween(DAY_START, NEXT_DAY)).thenReturn(new BigDecimal("100.00"));
        RevenueResponse r = service.getDailyRevenue(null);
        assertEquals(LocalDate.of(2026, 10, 30), r.date());
        assertEquals(new BigDecimal("100.00"), r.totalRevenue());
    }

    @Test
    void dayWithNoPaidBookingsIsZeroWithTwoDecimals() {
        when(repo.sumRevenueBetween(any(), any())).thenReturn(BigDecimal.ZERO);
        RevenueResponse r = service.getDailyRevenue(LocalDate.of(2026, 1, 1));
        assertEquals(new BigDecimal("0.00"), r.totalRevenue());
        assertEquals(0, r.paidBookings());
    }

    @Test
    void nullSumIsTreatedAsZero() {
        when(repo.sumRevenueBetween(any(), any())).thenReturn(null);
        assertEquals(new BigDecimal("0.00"), service.getDailyRevenue(LocalDate.of(2026, 1, 1)).totalRevenue());
    }
}
