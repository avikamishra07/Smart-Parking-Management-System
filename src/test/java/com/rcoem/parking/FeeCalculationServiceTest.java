package com.rcoem.parking;

import com.rcoem.parking.exception.InvalidParkingTimeException;
import com.rcoem.parking.services.FeeCalculationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class FeeCalculationServiceTest {

    private static final LocalDateTime ENTRY = LocalDateTime.of(2026, 10, 30, 10, 0, 0);
    private final FeeCalculationService service = new FeeCalculationService(3, new BigDecimal("50"));

    private BigDecimal fee(long minutes, long seconds) {
        return service.calculateFee(ENTRY, ENTRY.plusMinutes(minutes).plusSeconds(seconds));
    }

    @Test
    void zeroDurationCostsNothing() {
        assertEquals(new BigDecimal("0.00"), fee(0, 0));
        assertEquals(0, service.calculateBlocks(ENTRY, ENTRY));
    }

    @Test
    void oneSecondStartsFirstBlock() {
        assertEquals(new BigDecimal("50.00"), fee(0, 1));
    }

    @Test
    void assignmentExamplesAreCorrect() {
        assertEquals(new BigDecimal("50.00"), fee(135, 0)); // 2 hours 15 minutes
        assertEquals(new BigDecimal("100.00"), fee(185, 0)); // 3 hours 5 minutes
    }

    @Test
    void exactlyThreeHoursIsOneBlock() {
        assertEquals(new BigDecimal("50.00"), fee(180, 0));
    }

    @Test
    void oneSecondOverThreeHoursIsTwoBlocks() {
        assertEquals(new BigDecimal("100.00"), fee(180, 1));
    }

    @Test
    void fractionalSecondOverThreeHoursStartsAnotherBlock() {
        assertEquals(2, service.calculateBlocks(ENTRY, ENTRY.plusHours(3).plusNanos(1)));
        assertEquals(new BigDecimal("100.00"),
                service.calculateFee(ENTRY, ENTRY.plusHours(3).plusNanos(1)));
    }

    @Test
    void oneMinuteOverThreeHoursIsTwoBlocks() {
        assertEquals(new BigDecimal("100.00"), fee(181, 0));
    }

    @Test
    void exactlySixHoursIsTwoBlocks() {
        assertEquals(new BigDecimal("100.00"), fee(360, 0));
    }

    @Test
    void sixHoursOneMinuteIsThreeBlocks() {
        assertEquals(new BigDecimal("150.00"), fee(361, 0));
    }

    @ParameterizedTest
    @CsvSource({"1,1", "179,1", "180,1", "181,2", "359,2", "360,2", "361,3", "1440,8"})
    void blocksForMinutes(long minutes, long expectedBlocks) {
        assertEquals(expectedBlocks, service.calculateBlocks(ENTRY, ENTRY.plusMinutes(minutes)));
    }

    @Test
    void multiDayStayIsCalculated() {
        // 2 days + 1 minute = 2881 min -> ceil(2881/180) = 17 blocks
        assertEquals(new BigDecimal("850.00"), service.calculateFee(ENTRY, ENTRY.plusDays(2).plusMinutes(1)));
    }

    @Test
    void nullEntryIsRejected() {
        assertThrows(InvalidParkingTimeException.class, () -> service.calculateFee(null, ENTRY));
    }

    @Test
    void nullExitIsRejected() {
        assertThrows(InvalidParkingTimeException.class, () -> service.calculateFee(ENTRY, null));
    }

    @Test
    void bothNullAreRejected() {
        assertThrows(InvalidParkingTimeException.class, () -> service.calculateBlocks(null, null));
    }

    @Test
    void exitBeforeEntryIsRejected() {
        assertThrows(InvalidParkingTimeException.class, () -> service.calculateFee(ENTRY, ENTRY.minusSeconds(1)));
    }

    @Test
    void customRateAndBlockSizeAreHonoured() {
        FeeCalculationService hourly = new FeeCalculationService(1, new BigDecimal("20.50"));
        assertEquals(new BigDecimal("41.00"), hourly.calculateFee(ENTRY, ENTRY.plusMinutes(61)));
    }

    @Test
    void invalidConfigurationIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> new FeeCalculationService(0, BigDecimal.TEN));
        assertThrows(IllegalArgumentException.class, () -> new FeeCalculationService(3, new BigDecimal("-1")));
        assertThrows(IllegalArgumentException.class, () -> new FeeCalculationService(3, null));
    }
}
