package com.slotguard.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public class ReservationRequest {

    @NotNull(message = "Slot ID is required")
    private Long slotId;

    @NotBlank(message = "User name is required")
    private String userName;

    public ReservationRequest() {}

    public ReservationRequest(Long slotId, String userName) {
        this.slotId = slotId;
        this.userName = userName;
    }

    public Long getSlotId() { return slotId; }
    public void setSlotId(Long slotId) { this.slotId = slotId; }

    public String getUserName() { return userName; }
    public void setUserName(String userName) { this.userName = userName; }
}
