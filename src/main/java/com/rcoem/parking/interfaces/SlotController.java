package com.rcoem.parking.interfaces;

import com.rcoem.parking.dto.SlotResponse;
import com.rcoem.parking.services.SlotService;
import jakarta.validation.constraints.Positive;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/slots")
public class SlotController {

    private final SlotService slotService;

    public SlotController(SlotService slotService) {
        this.slotService = slotService;
    }

    @GetMapping
    public List<SlotResponse> getAllSlots() {
        return slotService.getAllSlots();
    }

    // declared before "/{id}" for readability; Spring ranks literal paths above variables anyway
    @GetMapping("/available")
    public List<SlotResponse> getAvailableSlots() {
        return slotService.getAvailableSlots();
    }

    @GetMapping("/{id}")
    public SlotResponse getSlotById(@PathVariable @Positive(message = "Slot id must be positive") Long id) {
        return slotService.getSlotById(id);
    }
}
