package com.rcoem.parking.domain;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface VehicleBookingRepository extends JpaRepository<VehicleBooking, Long> {

    @Query("select b from VehicleBooking b join fetch b.slot where b.bookingStatus = :status order by b.entryTime asc")
    List<VehicleBooking> findByBookingStatusWithSlot(@Param("status") BookingStatus status);

    boolean existsByVehicleNumberAndBookingStatus(String vehicleNumber, BookingStatus status);

    Optional<VehicleBooking> findBySlotIdAndBookingStatus(Long slotId, BookingStatus status);

    /** Locks the booking row so two checkouts of the same booking cannot both succeed. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from VehicleBooking b join fetch b.slot where b.id = :id")
    Optional<VehicleBooking> findByIdForUpdate(@Param("id") Long id);

    /** Revenue = completed + paid bookings whose checkout falls in [start, end). */
    @Query("""
            select coalesce(sum(b.fee), 0) from VehicleBooking b
            where b.bookingStatus = com.rcoem.parking.domain.BookingStatus.COMPLETED
              and b.paymentStatus = com.rcoem.parking.domain.PaymentStatus.PAID
              and b.exitTime >= :start and b.exitTime < :end
            """)
    BigDecimal sumRevenueBetween(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    /** Number of bookings behind the revenue figure (same filter as sumRevenueBetween). */
    @Query("""
            select count(b) from VehicleBooking b
            where b.bookingStatus = com.rcoem.parking.domain.BookingStatus.COMPLETED
              and b.paymentStatus = com.rcoem.parking.domain.PaymentStatus.PAID
              and b.exitTime >= :start and b.exitTime < :end
            """)
    long countRevenueBookingsBetween(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);
}
