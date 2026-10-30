package com.rcoem.parking.services;

import com.rcoem.parking.domain.VehicleBookingRepository;
import com.rcoem.parking.dto.RevenueResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Service
@Transactional(readOnly = true)
public class ReportService {

    private final VehicleBookingRepository bookingRepository;
    private final Clock clock;

    public ReportService(VehicleBookingRepository bookingRepository, Clock clock) {
        this.bookingRepository = bookingRepository;
        this.clock = clock;
    }

    /**
     * Revenue for one calendar day (defaults to today). A booking counts on the day it was checked
     * out, and only once it is PAID. The range is [00:00 of the day, 00:00 of the next day).
     */
    public RevenueResponse getDailyRevenue(LocalDate date) {
        LocalDate day = date != null ? date : LocalDate.now(clock);
        LocalDateTime start = day.atStartOfDay();
        LocalDateTime end = day.plusDays(1).atStartOfDay();

        BigDecimal total = bookingRepository.sumRevenueBetween(start, end);
        BigDecimal revenue = (total == null ? BigDecimal.ZERO : total).setScale(2, RoundingMode.HALF_UP);
        long count = bookingRepository.countRevenueBookingsBetween(start, end);
        return new RevenueResponse(day, revenue, count);
    }
}
