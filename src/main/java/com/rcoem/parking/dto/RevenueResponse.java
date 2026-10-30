package com.rcoem.parking.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Daily revenue: fees of PAID, COMPLETED bookings whose checkout happened on {@code date}. */
public record RevenueResponse(LocalDate date, BigDecimal totalRevenue, long paidBookings) {
}
