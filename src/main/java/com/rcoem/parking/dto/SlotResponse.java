package com.rcoem.parking.dto;

import com.rcoem.parking.domain.ParkingSlot;
import com.rcoem.parking.domain.SlotStatus;

public record SlotResponse(Long id, String slotNumber, SlotStatus status) {

    public static SlotResponse from(ParkingSlot slot) {
        return new SlotResponse(slot.getId(), slot.getSlotNumber(), slot.getStatus());
    }
}
