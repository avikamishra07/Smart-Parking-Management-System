package com.rcoem.parking.domain;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ParkingSlotRepository extends JpaRepository<ParkingSlot, Long> {

    List<ParkingSlot> findByStatusOrderBySlotNumberAsc(SlotStatus status);

    List<ParkingSlot> findAllByOrderBySlotNumberAsc();

    Optional<ParkingSlot> findBySlotNumber(String slotNumber);

    /** Row-level lock used by entry/checkout to serialize concurrent requests on one slot. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from ParkingSlot s where s.id = :id")
    Optional<ParkingSlot> findByIdForUpdate(@Param("id") Long id);
}
