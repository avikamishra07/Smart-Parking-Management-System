package com.rcoem.parking.services;

import com.rcoem.parking.domain.ParkingSlotRepository;
import com.rcoem.parking.domain.SlotStatus;
import com.rcoem.parking.dto.SlotResponse;
import com.rcoem.parking.exception.SlotNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class SlotService {

    private final ParkingSlotRepository slotRepository;

    public SlotService(ParkingSlotRepository slotRepository) {
        this.slotRepository = slotRepository;
    }

    public List<SlotResponse> getAllSlots() {
        return slotRepository.findAllByOrderBySlotNumberAsc().stream().map(SlotResponse::from).toList();
    }

    public List<SlotResponse> getAvailableSlots() {
        return slotRepository.findByStatusOrderBySlotNumberAsc(SlotStatus.AVAILABLE)
                .stream().map(SlotResponse::from).toList();
    }

    public SlotResponse getSlotById(Long id) {
        return slotRepository.findById(id)
                .map(SlotResponse::from)
                .orElseThrow(() -> new SlotNotFoundException(id));
    }
}
