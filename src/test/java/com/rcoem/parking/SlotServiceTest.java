package com.rcoem.parking;

import com.rcoem.parking.domain.ParkingSlot;
import com.rcoem.parking.domain.ParkingSlotRepository;
import com.rcoem.parking.domain.SlotStatus;
import com.rcoem.parking.dto.SlotResponse;
import com.rcoem.parking.exception.SlotNotFoundException;
import com.rcoem.parking.services.SlotService;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SlotServiceTest {

    private final ParkingSlotRepository repo = mock(ParkingSlotRepository.class);
    private final SlotService service = new SlotService(repo);

    private ParkingSlot slot(long id, String number, SlotStatus status) {
        return ParkingSlot.builder().id(id).slotNumber(number).status(status).version(0L).build();
    }

    @Test
    void getAllSlotsMapsEveryRow() {
        when(repo.findAllByOrderBySlotNumberAsc())
                .thenReturn(List.of(slot(1, "A-01", SlotStatus.AVAILABLE), slot(2, "A-02", SlotStatus.OCCUPIED)));
        List<SlotResponse> result = service.getAllSlots();
        assertEquals(2, result.size());
        assertEquals(new SlotResponse(2L, "A-02", SlotStatus.OCCUPIED), result.get(1));
    }

    @Test
    void getAvailableSlotsQueriesAvailableStatusOnly() {
        when(repo.findByStatusOrderBySlotNumberAsc(SlotStatus.AVAILABLE))
                .thenReturn(List.of(slot(1, "A-01", SlotStatus.AVAILABLE)));
        assertEquals(1, service.getAvailableSlots().size());
        verify(repo).findByStatusOrderBySlotNumberAsc(SlotStatus.AVAILABLE);
    }

    @Test
    void getAvailableSlotsReturnsEmptyListWhenNoneFree() {
        when(repo.findByStatusOrderBySlotNumberAsc(SlotStatus.AVAILABLE)).thenReturn(List.of());
        assertTrue(service.getAvailableSlots().isEmpty());
    }

    @Test
    void getSlotByIdReturnsSlot() {
        when(repo.findById(5L)).thenReturn(Optional.of(slot(5, "B-02", SlotStatus.AVAILABLE)));
        assertEquals("B-02", service.getSlotById(5L).slotNumber());
    }

    @Test
    void getSlotByIdThrowsWhenMissing() {
        when(repo.findById(99L)).thenReturn(Optional.empty());
        assertThrows(SlotNotFoundException.class, () -> service.getSlotById(99L));
    }
}
