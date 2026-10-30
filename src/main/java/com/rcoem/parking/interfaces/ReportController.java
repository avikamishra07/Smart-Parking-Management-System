package com.rcoem.parking.interfaces;

import com.rcoem.parking.dto.RevenueResponse;
import com.rcoem.parking.services.ReportService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/reports")
public class ReportController {

    private final ReportService reportService;

    public ReportController(ReportService reportService) {
        this.reportService = reportService;
    }

    /** GET /reports/revenue?date=2026-10-30 (date optional, defaults to today). */
    @GetMapping("/revenue")
    public RevenueResponse getDailyRevenue(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return reportService.getDailyRevenue(date);
    }
}
