package com.slotguard.application.dto;

import com.slotguard.application.model.ConcurrencyMode;
import jakarta.validation.constraints.NotNull;

public class ConcurrencyModeRequest {

    @NotNull(message = "Mode is required (VULNERABLE or SAFE)")
    private ConcurrencyMode mode;

    public ConcurrencyModeRequest() {}

    public ConcurrencyModeRequest(ConcurrencyMode mode) {
        this.mode = mode;
    }

    public ConcurrencyMode getMode() { return mode; }
    public void setMode(ConcurrencyMode mode) { this.mode = mode; }
}
