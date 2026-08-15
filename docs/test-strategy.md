# SlotGuard — Test Strategy

## Core Testing Principle

Every test in SlotGuard ultimately verifies one invariant:

```
successful_reservations <= slot_capacity
```

This is the **Key Invariant**. All test types from functional to load are structured around proving or disproving this rule.

---

## Test Pyramid

```
                    ╔═══════════════════╗
                    ║  Load Tests       ║  JMeter (1000 users)
                    ║  JMeter           ║
                 ╔══╩═══════════════════╩══╗
                 ║  UI Tests               ║  Selenium, Playwright
                 ║  Cross-Browser          ║
              ╔══╩═════════════════════════╩══╗
              ║  API + Concurrency Tests       ║  REST Assured
              ║  DB Invariant Tests            ║  SQL + JDBC
           ╔══╩════════════════════════════════╩══╗
           ║  Unit Tests                          ║  JUnit + MockMvc
           ╚══════════════════════════════════════╝
```

---

## Test Suites

### 1. Unit Tests (Spring Boot — `application/src/test/`)
**Tool:** JUnit 5 + MockMvc + Spring Test  
**Run:** `mvn test` inside `application/`

| Test Class | What it verifies |
|---|---|
| `SlotControllerTest` | POST /api/slots, GET /api/slots, GET /api/slots/{id}/invariant |
| `ConfigControllerTest` | GET/POST /api/config/concurrency-mode |
| `ReservationServiceTest` | SAFE mode concurrency: 20 threads, capacity=1 → exactly 1 success |

**Key test: `testConcurrentReservationsInSafeMode`**
```java
// 20 threads, capacity = 1, SAFE mode
assertEquals(1, successCount.get());   // MUST be exactly 1
assertEquals(19, rejectedCount.get()); // MUST be exactly 19
```

---

### 2. API Tests (REST Assured — `automation/`)
**Tool:** REST Assured  
**Group:** `api`

| Test | Assertion |
|---|---|
| `testCreateSlotApi` | HTTP 201, `data.reservedCount == 0` |
| `testGetAllSlotsApi` | HTTP 200, `data` is a list |
| `testGetSlotInvariantStatusApi` | `data.doubleBooked == false`, `data.status == "INVARIANT_HOLDING"` |
| `testMakeReservationApiSuccess` | HTTP 201 on first booking |
| `testMakeReservationApiExceedCapacity` | HTTP 409 on second booking of capacity=1 slot |

---

### 3. Database Invariant Tests (JDBC — `automation/`)
**Tool:** Raw JDBC + TestNG  
**Group:** `database`

```sql
-- Test 1: check invariant_status across all slots
SELECT slot_id, actual_db_reservations, invariant_status
FROM slot_invariants_view;
-- Asserts: invariant_status = 'INVARIANT_HOLDING' for all rows

-- Test 2: reserved_count matches actual reservation rows
SELECT s.id, s.reserved_count, COUNT(r.id) AS actual_count
FROM slots s LEFT JOIN reservations r ON s.id = r.slot_id
GROUP BY s.id, s.reserved_count;
-- Asserts: reserved_count == actual_count for all slots
```

---

### 4. Concurrency Tests (REST Assured + Java Threads — `automation/`)
**Tool:** REST Assured + `ExecutorService` + `CountDownLatch`  
**Group:** `concurrency`

This is the **most important test suite**. A `CountDownLatch` synchronises all threads to fire at exactly the same moment, producing maximum contention.

| Scenario | Capacity | Concurrent Users | Expected Successes | Mode |
|---|---|---|---|---|
| A | 1 | 10 | 1 | SAFE |
| B | 1 | 100 | 1 | SAFE |
| C | 5 | 100 | 5 | SAFE |
| D | 1 | 20 | >1 (defect demo) | VULNERABLE |

**Key test pattern:**
```java
CountDownLatch startLatch = new CountDownLatch(1);
// Start N threads, all blocked on latch.await()
startLatch.countDown(); // Fire all simultaneously
// Assert success count == capacity
// Assert DB invariant via /api/slots/{id}/invariant
```

---

### 5. Functional UI Tests (Selenium — `automation/`)
**Tool:** Selenium WebDriver (Chrome, headless)  
**Group:** `ui`

| Test | What it exercises |
|---|---|
| `testUiDashboardHeaderAndTitle` | Page loads, header shows "SlotGuard", mode badge visible |
| `testCreateSlotAndReserveUi` | Fill form → click Create → verify slot appears in grid |

---

### 6. Cross-Browser Tests (Playwright — `automation/`)
**Tool:** Microsoft Playwright (Chromium, Firefox, WebKit)  
**Group:** `cross-browser`

Tests the same reservation workflow on 3 browser engines via `@DataProvider`:
```java
@DataProvider
public Object[][] browserEngineProvider() {
    return new Object[][]{{"chromium"}, {"firefox"}, {"webkit"}};
}
```

**Asserts:** Slot creation workflow works identically across all browsers.

---

### 7. Load Tests (JMeter — `performance/`)
**Tool:** Apache JMeter 5.6  
**Plan:** `performance/slotguard-load-test.jmx`

| Scenario | Users | Capacity | Key Assertion |
|---|---|---|---|
| A | 10 | 1 | HTTP 201 or 409 only |
| B | 100 | 1 | HTTP 201 or 409 only |
| Teardown | — | — | `INVARIANT_HOLDING` in invariant API |

Run: `jmeter -n -t performance/slotguard-load-test.jmx -l results.jtl`

---

## Allure Report Contents

The Allure report answers:
- Which tests passed/failed?
- How many concurrent users were tested?
- How many successful vs rejected reservations?
- Was the database invariant maintained?
- Did any double-booking occur?
- Which browser engines were tested?
- What was the average/P95 response time?

---

## Running Tests

### Local (API tests only — requires running app)
```bash
# 1. Start the application
cd application && mvn spring-boot:run

# 2. Run all automation tests
cd automation && mvn test
```

### Local (full suite via Docker)
```bash
docker compose -f docker/docker-compose.yml --profile tests up --build
```

### Generate Allure Report
```bash
cd automation
mvn allure:serve   # Opens interactive report in browser
# OR
mvn allure:report  # Generates static HTML in target/site/allure-maven-plugin/
```
