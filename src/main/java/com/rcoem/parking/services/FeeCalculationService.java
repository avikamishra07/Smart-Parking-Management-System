package com.rcoem.parking.services;

import com.rcoem.parking.exception.InvalidParkingTimeException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;

/**
 * Fee = (number of started blocks) x (rate per block).
 * Rules:
 *  - duration 0            -> 0 blocks -> fee 0.00
 *  - exactly N x block     -> N blocks (3h00m00s = 1 block)
 *  - any started extra time -> one more block (3h00m01s = 2 blocks)
 *  - null timestamp, or exit before entry -> InvalidParkingTimeException
 * Integer arithmetic on seconds, so there is no floating-point error.
 */
@Service
public class FeeCalculationService {

    private final long blockSeconds;
    private final BigDecimal ratePerBlock;

    public FeeCalculationService(@Value("${parking.fee.block-hours:3}") int blockHours,
                                 @Value("${parking.fee.rate-per-block:50}") BigDecimal ratePerBlock) {
        if (blockHours <= 0) {
            throw new IllegalArgumentException("blockHours must be positive");
        }
        if (ratePerBlock == null || ratePerBlock.signum() < 0) {
            throw new IllegalArgumentException("ratePerBlock must not be negative");
        }
        this.blockSeconds = Duration.ofHours(blockHours).toSeconds();
        this.ratePerBlock = ratePerBlock;
    }

    /** Number of started blocks between entry and exit. */
    public long calculateBlocks(LocalDateTime entryTime, LocalDateTime exitTime) {
        if (entryTime == null || exitTime == null) {
            throw new InvalidParkingTimeException("Entry time and exit time must not be null");
        }
        if (exitTime.isBefore(entryTime)) {
            throw new InvalidParkingTimeException("Exit time must not be before entry time");
        }
        Duration duration = Duration.between(entryTime, exitTime);
        long seconds = duration.getSeconds();
        // Round any fractional second up: 3 hours + even 1 nanosecond starts block 2.
        if (duration.getNano() > 0) {
            seconds++;
        }
        // Ceiling division without floating point.
        return (seconds + blockSeconds - 1) / blockSeconds;
    }

    public BigDecimal calculateFee(LocalDateTime entryTime, LocalDateTime exitTime) {
        return ratePerBlock.multiply(BigDecimal.valueOf(calculateBlocks(entryTime, exitTime)))
                .setScale(2, RoundingMode.HALF_UP);
    }
}
