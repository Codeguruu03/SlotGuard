package com.slotguard.automation.database;

import com.slotguard.automation.config.TestConfig;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

public class ReservationDatabaseTest {

    @Test(groups = {"database", "regression"})
    public void testDatabaseInvariantHoldingForAllSlots() throws Exception {
        String query = "SELECT slot_id, title, capacity, reserved_count, actual_db_reservations, invariant_status " +
                       "FROM slot_invariants_view";

        try (Connection conn = TestConfig.getDatabaseConnection();
             PreparedStatement stmt = conn.prepareStatement(query);
             ResultSet rs = stmt.executeQuery()) {

            int checkedSlots = 0;
            while (rs.next()) {
                long slotId = rs.getLong("slot_id");
                String title = rs.getString("title");
                int capacity = rs.getInt("capacity");
                int actualDbReservations = rs.getInt("actual_db_reservations");
                String invariantStatus = rs.getString("invariant_status");

                checkedSlots++;

                // Assert invariant rule: actual_db_reservations <= capacity
                Assert.assertTrue(actualDbReservations <= capacity,
                        String.format("Slot ID %d (%s) violated capacity invariant! Capacity: %d, Actual DB Reservations: %d",
                                slotId, title, capacity, actualDbReservations));

                // Assert invariant status string
                Assert.assertEquals(invariantStatus, "INVARIANT_HOLDING",
                        String.format("Slot ID %d status reported %s instead of INVARIANT_HOLDING", slotId, invariantStatus));
            }

            System.out.println("SQL Database Invariant Validation PASSED across " + checkedSlots + " slots.");
        }
    }

    @Test(groups = {"database", "regression"})
    public void testReservationCountMatchesSlotRecord() throws Exception {
        String query = "SELECT s.id, s.reserved_count, COUNT(r.id) AS actual_count " +
                       "FROM slots s LEFT JOIN reservations r ON s.id = r.slot_id " +
                       "GROUP BY s.id, s.reserved_count";

        try (Connection conn = TestConfig.getDatabaseConnection();
             PreparedStatement stmt = conn.prepareStatement(query);
             ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) {
                long slotId = rs.getLong("id");
                int reservedCount = rs.getInt("reserved_count");
                int actualCount = rs.getInt("actual_count");

                Assert.assertEquals(reservedCount, actualCount,
                        String.format("Slot ID %d reserved_count (%d) does not match actual reservation records (%d)",
                                slotId, reservedCount, actualCount));
            }
        }
    }
}
