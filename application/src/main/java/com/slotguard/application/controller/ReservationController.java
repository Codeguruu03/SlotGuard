package com.slotguard.application.controller;

import com.slotguard.application.dto.ApiResponse;
import com.slotguard.application.dto.ReservationRequest;
import com.slotguard.application.model.Reservation;
import com.slotguard.application.service.ReservationService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/reservations")
@CrossOrigin(origins = "*")
public class ReservationController {

    private final ReservationService reservationService;

    public ReservationController(ReservationService reservationService) {
        this.reservationService = reservationService;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<Reservation>> makeReservation(@Valid @RequestBody ReservationRequest request) {
        Reservation reservation = reservationService.makeReservation(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Reservation confirmed", reservation));
    }

    @GetMapping("/slot/{slotId}")
    public ResponseEntity<ApiResponse<List<Reservation>>> getReservationsBySlot(@PathVariable Long slotId) {
        List<Reservation> reservations = reservationService.getReservationsBySlot(slotId);
        return ResponseEntity.ok(ApiResponse.success("Reservations retrieved successfully", reservations));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> cancelReservation(@PathVariable Long id) {
        reservationService.cancelReservation(id);
        return ResponseEntity.ok(ApiResponse.success("Reservation cancelled successfully", null));
    }
}
