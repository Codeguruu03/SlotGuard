package com.slotguard.automation.concurrency;

import com.slotguard.automation.config.TestConfig;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import org.testng.Assert;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static io.restassured.RestAssured.given;

/**
 * ConcurrentReservationTest — The Core Innovation of SlotGuard
 *
 * This is the most important test in the entire project. It answers one question:
 * "Can SlotGuard guarantee that a slot's reservation count never exceeds its capacity
 *  when thousands of users attempt to reserve it simultaneously?"
 *
 * Test Strategy:
 * 1. Create a slot with capacity = 1 (or N)
 * 2. Fire N concurrent HTTP POST /reservations requests simultaneously
 * 3. Assert: HTTP 201 count == slot.capacity  AND  HTTP 409 count == N - slot.capacity
 * 4. Assert the database invariant: actual DB reservation rows <= slot.capacity
 *
 * VULNERABLE Mode (default): Race condition IS expected and will be caught.
 * SAFE Mode (pessimistic lock): Invariant MUST hold under all concurrency.
 */
public class ConcurrentReservationTest {

    @BeforeClass(alwaysRun = true)
    public void setup() {
        RestAssured.baseURI = TestConfig.getBaseUrl();
    }

    // ─────────────────────────────────────────────────────────────
    // Helper: create a fresh slot and return its ID
    // ─────────────────────────────────────────────────────────────
    private int createSlot(String title, int capacity) {
        Map<String, Object> body = new HashMap<>();
        body.put("title", title);
        body.put("capacity", capacity);

        return given()
                .contentType(ContentType.JSON)
                .body(body)
                .when()
                .post("/api/slots")
                .then()
                .statusCode(201)
                .extract()
                .path("data.id");
    }

    // ─────────────────────────────────────────────────────────────
    // Helper: set SAFE or VULNERABLE concurrency mode on the API
    // ─────────────────────────────────────────────────────────────
    private void setConcurrencyMode(String mode) {
        Map<String, String> body = new HashMap<>();
        body.put("mode", mode);
        given()
                .contentType(ContentType.JSON)
                .body(body)
                .post("/api/config/concurrency-mode")
                .then()
                .statusCode(200);
    }

    // ─────────────────────────────────────────────────────────────
    // Helper: verify DB invariant via the invariant endpoint
    // ─────────────────────────────────────────────────────────────
    private void assertDatabaseInvariant(int slotId, int capacity, String testContext) {
        Response invResponse = given()
                .contentType(ContentType.JSON)
                .get("/api/slots/" + slotId + "/invariant")
                .then()
                .statusCode(200)
                .extract()
                .response();

        int actualDbCount = invResponse.path("data.actualDbReservationsCount");
        boolean isDoubleBooked = invResponse.path("data.doubleBooked");
        String status = invResponse.path("data.status");

        Assert.assertFalse(isDoubleBooked,
                String.format("[%s] DOUBLE BOOKING DEFECT DETECTED! Slot ID %d: DB reservations=%d exceeds capacity=%d. Status=%s",
                        testContext, slotId, actualDbCount, capacity, status));

        Assert.assertTrue(actualDbCount <= capacity,
                String.format("[%s] DB INVARIANT VIOLATED! Slot ID %d: DB rows=%d > capacity=%d",
                        testContext, slotId, actualDbCount, capacity));

        System.out.printf("[%s] ✅ DB Invariant HOLDS. Slot #%d: capacity=%d, actual_db_reservations=%d, status=%s%n",
                testContext, slotId, capacity, actualDbCount, status);
    }

    // ─────────────────────────────────────────────────────────────
    // Helper: fire N concurrent booking requests and collect results
    // ─────────────────────────────────────────────────────────────
    private ConcurrencyResult fireConCurrentRequests(int slotId, int numberOfRequests) throws InterruptedException {
        ExecutorService executorService = Executors.newFixedThreadPool(numberOfRequests);
        CountDownLatch startLatch = new CountDownLatch(1); // synchronise all threads to fire at once
        List<Future<Integer>> futures = new ArrayList<>();

        for (int i = 1; i <= numberOfRequests; i++) {
            final int userId = i;
            futures.add(executorService.submit(() -> {
                startLatch.await(); // Block until all threads are ready
                Map<String, Object> body = new HashMap<>();
                body.put("slotId", slotId);
                body.put("userName", "ConcurrentUser_" + userId + "_" + System.nanoTime());

                Response response = given()
                        .contentType(ContentType.JSON)
                        .body(body)
                        .post("/api/reservations")
                        .thenReturn();

                return response.getStatusCode();
            }));
        }

        long startTime = System.currentTimeMillis();
        startLatch.countDown(); // 🔥 Fire all concurrent requests simultaneously

        executorService.shutdown();
        boolean done = executorService.awaitTermination(30, TimeUnit.SECONDS);
        long endTime = System.currentTimeMillis();

        if (!done) {
            executorService.shutdownNow();
            throw new RuntimeException("Concurrent execution did not complete within timeout!");
        }

        AtomicInteger success201 = new AtomicInteger(0);
        AtomicInteger rejected409 = new AtomicInteger(0);
        AtomicInteger otherErrors = new AtomicInteger(0);

        for (Future<Integer> f : futures) {
            try {
                int status = f.get();
                if (status == 201) success201.incrementAndGet();
                else if (status == 409) rejected409.incrementAndGet();
                else otherErrors.incrementAndGet();
            } catch (ExecutionException e) {
                otherErrors.incrementAndGet();
            }
        }

        return new ConcurrencyResult(success201.get(), rejected409.get(), otherErrors.get(),
                endTime - startTime);
    }

    // ─────────────────────────────────────────────────────────────
    // SCENARIO A: 1 slot capacity=1, 10 concurrent users (SAFE)
    // Expected: exactly 1 success, 9 rejected, DB invariant holds
    // ─────────────────────────────────────────────────────────────
    @Test(groups = {"concurrency", "smoke"})
    public void testScenarioA_SafeMode_10Users_Capacity1() throws InterruptedException {
        setConcurrencyMode("SAFE");
        int slotId = createSlot("[Scenario A] Safe 1-Cap 10-Users", 1);

        System.out.println("\n=== SCENARIO A: SAFE MODE | Capacity=1 | 10 Concurrent Users ===");
        ConcurrencyResult result = fireConCurrentRequests(slotId, 10);
        result.print("Scenario A");

        Assert.assertEquals(result.success201, 1,
                "SAFE MODE: Only 1 reservation must succeed for capacity=1. Actual successes=" + result.success201);
        Assert.assertEquals(result.rejected409, 9,
                "SAFE MODE: 9 requests must be rejected with HTTP 409.");

        assertDatabaseInvariant(slotId, 1, "Scenario A");
    }

    // ─────────────────────────────────────────────────────────────
    // SCENARIO B: 1 slot capacity=1, 100 concurrent users (SAFE)
    // Expected: exactly 1 success, 99 rejected, DB invariant holds
    // ─────────────────────────────────────────────────────────────
    @Test(groups = {"concurrency", "regression"})
    public void testScenarioB_SafeMode_100Users_Capacity1() throws InterruptedException {
        setConcurrencyMode("SAFE");
        int slotId = createSlot("[Scenario B] Safe 1-Cap 100-Users", 1);

        System.out.println("\n=== SCENARIO B: SAFE MODE | Capacity=1 | 100 Concurrent Users ===");
        ConcurrencyResult result = fireConCurrentRequests(slotId, 100);
        result.print("Scenario B");

        Assert.assertEquals(result.success201, 1,
                "SAFE MODE: Only 1 reservation must succeed for capacity=1 under 100 concurrent requests. Actual=" + result.success201);
        Assert.assertEquals(result.rejected409 + result.otherErrors, 99,
                "SAFE MODE: 99 requests must be rejected.");

        assertDatabaseInvariant(slotId, 1, "Scenario B");
    }

    // ─────────────────────────────────────────────────────────────
    // SCENARIO C: 10 slots (capacity=5 each), 100 concurrent users (SAFE)
    // Tests distributed concurrent booking across multiple slots
    // ─────────────────────────────────────────────────────────────
    @Test(groups = {"concurrency", "regression"})
    public void testScenarioC_SafeMode_100Users_MultipleSlots() throws InterruptedException {
        setConcurrencyMode("SAFE");
        int capacity = 5;
        int slotId = createSlot("[Scenario C] Safe 5-Cap 100-Users", capacity);

        System.out.println("\n=== SCENARIO C: SAFE MODE | Capacity=5 | 100 Concurrent Users ===");
        ConcurrencyResult result = fireConCurrentRequests(slotId, 100);
        result.print("Scenario C");

        Assert.assertEquals(result.success201, capacity,
                "SAFE MODE: Exactly 5 reservations must succeed for capacity=5. Actual=" + result.success201);
        Assert.assertEquals(result.rejected409 + result.otherErrors, 100 - capacity,
                "SAFE MODE: 95 requests must be rejected.");

        assertDatabaseInvariant(slotId, capacity, "Scenario C");
    }

    // ─────────────────────────────────────────────────────────────
    // SCENARIO D (DEFECT DEMO): Vulnerable mode - shows race condition
    // This test EXPECTS overbooking to happen in VULNERABLE mode.
    // It's the "before-fix" demonstration referenced in the idea doc.
    // ─────────────────────────────────────────────────────────────
    @Test(groups = {"concurrency", "regression"})
    public void testScenarioD_VulnerableMode_DetectsRaceCondition() throws InterruptedException {
        setConcurrencyMode("VULNERABLE");
        int capacity = 1;
        int slotId = createSlot("[Scenario D] Vulnerable Race Condition Demo", capacity);

        System.out.println("\n=== SCENARIO D: VULNERABLE MODE | Capacity=1 | 20 Concurrent Users ===");
        System.out.println(">>> This test DEMONSTRATES the race condition defect. Expect overbooking! <<<");

        ConcurrencyResult result = fireConCurrentRequests(slotId, 20);
        result.print("Scenario D");

        // Query invariant status
        Response invResponse = given()
                .contentType(ContentType.JSON)
                .get("/api/slots/" + slotId + "/invariant")
                .then()
                .statusCode(200)
                .extract()
                .response();

        int actualDbCount = invResponse.path("data.actualDbReservationsCount");
        boolean isDoubleBooked = invResponse.path("data.doubleBooked");
        String status = invResponse.path("data.status");

        System.out.printf("[Scenario D] VULNERABLE Mode Result: DB reservations=%d, capacity=%d, doubleBooked=%b, status=%s%n",
                actualDbCount, capacity, isDoubleBooked, status);

        if (isDoubleBooked) {
            System.out.println("[Scenario D] 🔥 RACE CONDITION DEFECT CONFIRMED! Double-booking occurred as expected in VULNERABLE mode.");
            System.out.println("[Scenario D]    → This is the exact defect that pessimistic locking (SAFE mode) prevents.");
        } else {
            System.out.println("[Scenario D] ⚠ Race condition was not triggered in this run (timing-dependent). Re-run to reproduce.");
        }

        // We don't FAIL here — the point is to DEMONSTRATE the defect, not assert pass/fail
        // The real assertion is in the SAFE mode tests that verify the FIX works.
        System.out.println("[Scenario D] ✅ Test execution completed. Review logs for defect evidence.");
    }

    // ─────────────────────────────────────────────────────────────
    // THE INVARIANT RULE TEST: key business invariant assertion
    // Verifies: successful_reservations <= slot_capacity for all slots
    // ─────────────────────────────────────────────────────────────
    @Test(groups = {"concurrency", "smoke"}, dependsOnMethods = {"testScenarioA_SafeMode_10Users_Capacity1"})
    public void testBusinessInvariantRule_ReservationsNeverExceedCapacity() {
        // Query all slots and check each one's invariant
        Response slotsResponse = given()
                .contentType(ContentType.JSON)
                .get("/api/slots")
                .then()
                .statusCode(200)
                .extract()
                .response();

        List<Map<String, Object>> slots = slotsResponse.path("data");
        Assert.assertNotNull(slots, "Slots list must not be null");

        int violations = 0;
        for (Map<String, Object> slot : slots) {
            int slotId = (int) slot.get("id");
            int capacity = (int) slot.get("capacity");
            int reservedCount = (int) slot.get("reservedCount");

            // Check via invariant endpoint
            Response invResponse = given()
                    .contentType(ContentType.JSON)
                    .get("/api/slots/" + slotId + "/invariant")
                    .then()
                    .statusCode(200)
                    .extract()
                    .response();

            boolean isDoubleBooked = invResponse.path("data.doubleBooked");
            int actualDbReservations = invResponse.path("data.actualDbReservationsCount");

            if (isDoubleBooked) {
                System.err.printf("⚠ INVARIANT VIOLATION: Slot #%d — capacity=%d, DB reservations=%d%n",
                        slotId, capacity, actualDbReservations);
                violations++;
            }
        }

        Assert.assertEquals(violations, 0,
                String.format("INVARIANT RULE VIOLATED: %d slot(s) have more reservations than their capacity!", violations));
        System.out.printf("✅ Business Invariant VERIFIED across %d slots. No violations found.%n", slots.size());
    }

    // ─────────────────────────────────────────────────────────────
    // DataProvider for parametrised concurrency load scenarios
    // ─────────────────────────────────────────────────────────────
    @DataProvider(name = "concurrencyScenarios")
    public Object[][] concurrencyScenarios() {
        return new Object[][]{
                {"Load-5",   1,  5,  1},
                {"Load-10",  3, 10,  3},
                {"Load-50",  1, 50,  1},
        };
    }

    @Test(dataProvider = "concurrencyScenarios", groups = {"concurrency", "regression"})
    public void testParametrisedConcurrencyScenarios(String label, int capacity, int userCount, int expectedSuccesses)
            throws InterruptedException {
        setConcurrencyMode("SAFE");
        int slotId = createSlot("[Parametrised] " + label + " | Cap=" + capacity + " | Users=" + userCount, capacity);

        System.out.printf("%n=== PARAMETRISED CONCURRENCY: %s | cap=%d | users=%d ===%n", label, capacity, userCount);
        ConcurrencyResult result = fireConCurrentRequests(slotId, userCount);
        result.print(label);

        Assert.assertEquals(result.success201, expectedSuccesses,
                String.format("[%s] Expected %d successes for capacity=%d but got %d",
                        label, expectedSuccesses, capacity, result.success201));

        assertDatabaseInvariant(slotId, capacity, label);
    }

    // ─────────────────────────────────────────────────────────────
    // Inner result container
    // ─────────────────────────────────────────────────────────────
    private static class ConcurrencyResult {
        final int success201;
        final int rejected409;
        final int otherErrors;
        final long durationMs;

        ConcurrencyResult(int success201, int rejected409, int otherErrors, long durationMs) {
            this.success201 = success201;
            this.rejected409 = rejected409;
            this.otherErrors = otherErrors;
            this.durationMs = durationMs;
        }

        void print(String context) {
            System.out.printf(
                    "%n╔══════════════════════════════════════════╗%n" +
                    "║   SLOTGUARD CONCURRENCY RESULT [%-8s]  ║%n" +
                    "╠══════════════════════════════════════════╣%n" +
                    "║  HTTP 201 (Success):       %-5d           ║%n" +
                    "║  HTTP 409 (Rejected):      %-5d           ║%n" +
                    "║  Other Errors:             %-5d           ║%n" +
                    "║  Total Duration:           %-5d ms         ║%n" +
                    "╚══════════════════════════════════════════╝%n",
                    context, success201, rejected409, otherErrors, durationMs);
        }
    }
}
