package com.slotguard.application.controller;

import com.slotguard.application.dto.ApiResponse;
import com.slotguard.application.dto.SlotInvariantResponse;
import com.slotguard.application.dto.SlotRequest;
import com.slotguard.application.model.Slot;
import com.slotguard.application.service.SlotService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/slots")
@CrossOrigin(origins = "*")
public class SlotController {

    private final SlotService slotService;

    public SlotController(SlotService slotService) {
        this.slotService = slotService;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<Slot>> createSlot(@Valid @RequestBody SlotRequest request) {
        Slot createdSlot = slotService.createSlot(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Slot created successfully", createdSlot));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<Slot>>> getAllSlots() {
        List<Slot> slots = slotService.getAllSlots();
        return ResponseEntity.ok(ApiResponse.success("Slots retrieved successfully", slots));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<Slot>> getSlotById(@PathVariable Long id) {
        Slot slot = slotService.getSlotById(id);
        return ResponseEntity.ok(ApiResponse.success("Slot retrieved successfully", slot));
    }

    @GetMapping("/{id}/invariant")
    public ResponseEntity<ApiResponse<SlotInvariantResponse>> getSlotInvariantStatus(@PathVariable Long id) {
        SlotInvariantResponse response = slotService.getSlotInvariantStatus(id);
        return ResponseEntity.ok(ApiResponse.success("Slot invariant status retrieved", response));
    }
}
