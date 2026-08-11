package com.slotguard.application.service;

import com.slotguard.application.dto.SlotInvariantResponse;
import com.slotguard.application.dto.SlotRequest;
import com.slotguard.application.model.Slot;
import com.slotguard.application.repository.ReservationRepository;
import com.slotguard.application.repository.SlotRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class SlotService {

    private final SlotRepository slotRepository;
    private final ReservationRepository reservationRepository;

    public SlotService(SlotRepository slotRepository, ReservationRepository reservationRepository) {
        this.slotRepository = slotRepository;
        this.reservationRepository = reservationRepository;
    }

    @Transactional
    public Slot createSlot(SlotRequest request) {
        Slot slot = new Slot(request.getTitle(), request.getCapacity());
        return slotRepository.save(slot);
    }

    public List<Slot> getAllSlots() {
        return slotRepository.findAll();
    }

    public Slot getSlotById(Long id) {
        return slotRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Slot with ID " + id + " not found"));
    }

    public SlotInvariantResponse getSlotInvariantStatus(Long slotId) {
        Slot slot = getSlotById(slotId);
        long actualDbReservations = reservationRepository.countBySlotId(slotId);
        return new SlotInvariantResponse(
                slot.getId(),
                slot.getTitle(),
                slot.getCapacity(),
                slot.getReservedCount(),
                actualDbReservations
        );
    }
}
