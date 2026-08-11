package com.slotguard.application.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.slotguard.application.dto.SlotRequest;
import com.slotguard.application.model.Slot;
import com.slotguard.application.service.SlotService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
public class SlotControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private SlotService slotService;

    @Test
    @DisplayName("POST /api/slots - Should successfully create a new slot")
    public void testCreateSlotSuccess() throws Exception {
        SlotRequest request = new SlotRequest("Dr. Sharma Appointment", 2);

        mockMvc.perform(post("/api/slots")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.title").value("Dr. Sharma Appointment"))
                .andExpect(jsonPath("$.data.capacity").value(2))
                .andExpect(jsonPath("$.data.reservedCount").value(0));
    }

    @Test
    @DisplayName("GET /api/slots - Should return all available slots")
    public void testGetAllSlots() throws Exception {
        mockMvc.perform(get("/api/slots"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data", isA(java.util.List.class)));
    }

    @Test
    @DisplayName("GET /api/slots/{id}/invariant - Should return slot invariant status")
    public void testGetSlotInvariant() throws Exception {
        SlotRequest request = new SlotRequest("Invariant Check Slot", 1);
        Slot slot = slotService.createSlot(request);

        mockMvc.perform(get("/api/slots/" + slot.getId() + "/invariant"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.slotId").value(slot.getId()))
                .andExpect(jsonPath("$.data.capacity").value(1))
                .andExpect(jsonPath("$.data.doubleBooked").value(false));
    }
}
