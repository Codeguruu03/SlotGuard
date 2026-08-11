package com.slotguard.application.service;

import com.slotguard.application.dto.ReservationRequest;
import com.slotguard.application.model.ConcurrencyMode;
import com.slotguard.application.model.Reservation;
import com.slotguard.application.model.Slot;
import com.slotguard.application.repository.ReservationRepository;
import com.slotguard.application.repository.SlotRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ReservationService {

    private static final Logger log = LoggerFactory.getLogger(ReservationService.class);

    private final SlotRepository slotRepository;
    private final ReservationRepository reservationRepository;
    private final ConcurrencyConfigService configService;

    public ReservationService(SlotRepository slotRepository,
                              ReservationRepository reservationRepository,
                              ConcurrencyConfigService configService) {
        this.slotRepository = slotRepository;
        this.reservationRepository = reservationRepository;
        this.configService = configService;
    }

    public Reservation makeReservation(ReservationRequest request) {
        ConcurrencyMode currentMode = configService.getActiveMode();
        if (currentMode == ConcurrencyMode.SAFE) {
            return makeReservationSafe(request);
        } else {
            return makeReservationVulnerable(request);
        }
    }

    /**
     * VULNERABLE IMPLEMENTATION
     * Non-thread-safe check-then-act logic without pessimistic/optimistic locking or transactional isolation.
     * Artificially delays execution to demonstrate race condition defects under concurrent load.
     */
    public Reservation makeReservationVulnerable(ReservationRequest request) {
        Slot slot = slotRepository.findById(request.getSlotId())
                .orElseThrow(() -> new IllegalArgumentException("Slot with ID " + request.getSlotId() + " not found"));

        if (slot.getReservedCount() >= slot.getCapacity()) {
            throw new IllegalStateException("Slot capacity exceeded! Cannot reserve.");
        }

        // Artificial delay to simulate thread context-switch and expose race condition defect
        try {
            Thread.sleep(15);
        } catch (InterruptedException ignored) {}

        // Save reservation record
        Reservation reservation = new Reservation(slot.getId(), request.getUserName());
        Reservation savedReservation = reservationRepository.save(reservation);

        // Update slot reserved count
        slot.setReservedCount(slot.getReservedCount() + 1);
        slotRepository.save(slot);

        log.warn("VULNERABLE BOOKING: Slot ID {} booked by {}", slot.getId(), request.getUserName());
        return savedReservation;
    }

    /**
     * SAFE IMPLEMENTATION
     * Uses Pessimistic Write Lock (SELECT FOR UPDATE) inside a transactional boundary to prevent double-booking.
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Reservation makeReservationSafe(ReservationRequest request) {
        Slot slot = slotRepository.findByIdWithPessimisticLock(request.getSlotId())
                .orElseThrow(() -> new IllegalArgumentException("Slot with ID " + request.getSlotId() + " not found"));

        if (slot.getReservedCount() >= slot.getCapacity()) {
            throw new IllegalStateException("Slot capacity exceeded! Cannot reserve.");
        }

        slot.setReservedCount(slot.getReservedCount() + 1);
        slotRepository.save(slot);

        Reservation reservation = new Reservation(slot.getId(), request.getUserName());
        Reservation savedReservation = reservationRepository.save(reservation);

        log.info("SAFE BOOKING: Slot ID {} locked and booked by {}", slot.getId(), request.getUserName());
        return savedReservation;
    }

    public List<Reservation> getReservationsBySlot(Long slotId) {
        return reservationRepository.findBySlotId(slotId);
    }

    @Transactional
    public void cancelReservation(Long reservationId) {
        Reservation reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new IllegalArgumentException("Reservation with ID " + reservationId + " not found"));

        Slot slot = slotRepository.findByIdWithPessimisticLock(reservation.getSlotId())
                .orElseThrow(() -> new IllegalArgumentException("Slot not found"));

        if (slot.getReservedCount() > 0) {
            slot.setReservedCount(slot.getReservedCount() - 1);
            slotRepository.save(slot);
        }

        reservationRepository.delete(reservation);
    }
}
