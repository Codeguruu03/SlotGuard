package com.slotguard.application.service;

import com.slotguard.application.dto.ReservationRequest;
import com.slotguard.application.dto.SlotRequest;
import com.slotguard.application.model.ConcurrencyMode;
import com.slotguard.application.model.Reservation;
import com.slotguard.application.model.Slot;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
public class ReservationServiceTest {

    @Autowired
    private ReservationService reservationService;

    @Autowired
    private SlotService slotService;

    @Autowired
    private ConcurrencyConfigService configService;

    @Test
    @DisplayName("SAFE Mode - Concurrent requests should strictly maintain reservation <= capacity invariant")
    public void testConcurrentReservationsInSafeMode() throws InterruptedException {
        // Set mode to SAFE
        configService.setActiveMode(ConcurrencyMode.SAFE);

        // Create slot with capacity 1
        Slot slot = slotService.createSlot(new SlotRequest("Concurrency Test Slot SAFE", 1));

        int numberOfThreads = 20;
        ExecutorService executorService = Executors.newFixedThreadPool(numberOfThreads);
        CountDownLatch latch = new CountDownLatch(1);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger rejectedCount = new AtomicInteger(0);

        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < numberOfThreads; i++) {
            final int userId = i;
            futures.add(executorService.submit(() -> {
                try {
                    latch.await(); // Synchronize all threads to start at the exact same moment
                    ReservationRequest request = new ReservationRequest(slot.getId(), "User_" + userId);
                    Reservation reservation = reservationService.makeReservation(request);
                    if (reservation != null) {
                        successCount.incrementAndGet();
                    }
                } catch (IllegalStateException e) {
                    rejectedCount.incrementAndGet();
                } catch (Exception e) {
                    // Unexpected error
                }
            }));
        }

        latch.countDown(); // Unblock threads simultaneously
        executorService.shutdown();
        boolean finished = executorService.awaitTermination(10, TimeUnit.SECONDS);
        assertTrue(finished, "Execution completed within timeout");

        // Verify invariant: Only 1 booking should succeed, 19 rejected
        assertEquals(1, successCount.get(), "Only 1 reservation must succeed for capacity=1 in SAFE mode");
        assertEquals(19, rejectedCount.get(), "19 reservations must be rejected");

        // DB check
        Slot updatedSlot = slotService.getSlotById(slot.getId());
        assertEquals(1, updatedSlot.getReservedCount(), "Database slot reserved count must equal 1");
    }

    @Test
    @DisplayName("VULNERABLE Mode - Concurrent requests reproduce race condition & overbooking defect")
    public void testConcurrentReservationsInVulnerableMode() throws InterruptedException {
        // Set mode to VULNERABLE
        configService.setActiveMode(ConcurrencyMode.VULNERABLE);

        // Create slot with capacity 1
        Slot slot = slotService.createSlot(new SlotRequest("Concurrency Test Slot VULNERABLE", 1));

        int numberOfThreads = 5;
        ExecutorService executorService = Executors.newFixedThreadPool(numberOfThreads);
        CountDownLatch latch = new CountDownLatch(1);

        AtomicInteger successCount = new AtomicInteger(0);

        for (int i = 0; i < numberOfThreads; i++) {
            final int userId = i;
            executorService.submit(() -> {
                try {
                    latch.await(); // Synchronize all threads
                    ReservationRequest request = new ReservationRequest(slot.getId(), "User_" + userId);
                    Reservation reservation = reservationService.makeReservation(request);
                    if (reservation != null) {
                        successCount.incrementAndGet();
                    }
                } catch (Exception ignored) {
                }
            });
        }

        latch.countDown(); // Fire simultaneous requests
        executorService.shutdown();
        executorService.awaitTermination(10, TimeUnit.SECONDS);

        // In vulnerable mode, artificial context switch causes > 1 bookings to succeed!
        assertTrue(successCount.get() > 1, "Vulnerable mode should reproduce race condition overbooking (>1 successes for capacity 1)");

        // DB invariant check shows defect
        var invariantStatus = slotService.getSlotInvariantStatus(slot.getId());
        assertTrue(invariantStatus.isDoubleBooked(), "Invariant check must report double booked defect in vulnerable mode");
    }
}
