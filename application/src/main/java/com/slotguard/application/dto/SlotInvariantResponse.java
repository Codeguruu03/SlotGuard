package com.slotguard.application.dto;

public class SlotInvariantResponse {

    private Long slotId;
    private String title;
    private Integer capacity;
    private Integer slotReservedCount;
    private Long actualDbReservationsCount;
    private String status; // INVARIANT_HOLDING or DOUBLE_BOOKED
    private boolean doubleBooked;

    public SlotInvariantResponse() {}

    public SlotInvariantResponse(Long slotId, String title, Integer capacity, Integer slotReservedCount, Long actualDbReservationsCount) {
        this.slotId = slotId;
        this.title = title;
        this.capacity = capacity;
        this.slotReservedCount = slotReservedCount;
        this.actualDbReservationsCount = actualDbReservationsCount;
        this.doubleBooked = actualDbReservationsCount > capacity;
        this.status = this.doubleBooked ? "DOUBLE_BOOKED" : "INVARIANT_HOLDING";
    }

    public Long getSlotId() { return slotId; }
    public void setSlotId(Long slotId) { this.slotId = slotId; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public Integer getCapacity() { return capacity; }
    public void setCapacity(Integer capacity) { this.capacity = capacity; }

    public Integer getSlotReservedCount() { return slotReservedCount; }
    public void setSlotReservedCount(Integer slotReservedCount) { this.slotReservedCount = slotReservedCount; }

    public Long getActualDbReservationsCount() { return actualDbReservationsCount; }
    public void setActualDbReservationsCount(Long actualDbReservationsCount) { this.actualDbReservationsCount = actualDbReservationsCount; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public boolean isDoubleBooked() { return doubleBooked; }
    public void setDoubleBooked(boolean doubleBooked) { this.doubleBooked = doubleBooked; }
}
