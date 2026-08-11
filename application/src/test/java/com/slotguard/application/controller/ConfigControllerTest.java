package com.slotguard.application.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.slotguard.application.dto.ConcurrencyModeRequest;
import com.slotguard.application.model.ConcurrencyMode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
public class ConfigControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("GET /api/config/concurrency-mode - Should return default mode")
    public void testGetConcurrencyMode() throws Exception {
        mockMvc.perform(get("/api/config/concurrency-mode"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").exists());
    }

    @Test
    @DisplayName("POST /api/config/concurrency-mode - Should switch mode between VULNERABLE and SAFE")
    public void testSetConcurrencyMode() throws Exception {
        ConcurrencyModeRequest request = new ConcurrencyModeRequest(ConcurrencyMode.SAFE);

        mockMvc.perform(post("/api/config/concurrency-mode")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").value("SAFE"));

        // Switch back to VULNERABLE
        request = new ConcurrencyModeRequest(ConcurrencyMode.VULNERABLE);

        mockMvc.perform(post("/api/config/concurrency-mode")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").value("VULNERABLE"));
    }
}
