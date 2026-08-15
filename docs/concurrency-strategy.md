# SlotGuard — Concurrency Strategy

## The Problem

Race conditions in reservation systems are silent and intermittent. They only appear under concurrent load and are virtually invisible in sequential testing.

Consider this sequence for a slot with `capacity = 1`:

```
Thread A: reads reserved_count = 0  ✓ (can book)
Thread B: reads reserved_count = 0  ✓ (can book)
Thread A: writes reserved_count = 1, saves reservation
Thread B: writes reserved_count = 1, saves reservation  ← DOUBLE BOOKING!
```

Both threads read `0` before either writes `1`. This is the classic **check-then-act** race condition.

---

## Why Standard Testing Misses This

A sequential test can never expose this defect:
```
Test: reserve slot → HTTP 201 ✓
Test: reserve again → HTTP 409 ✓
```

Both pass. Yet in production, under 1000 concurrent users, 2+ reservations will succeed.

---

## VULNERABLE Implementation

The application ships with an intentionally broken implementation that can be toggled at runtime:

```java
// VULNERABLE: No locking, artificial delay amplifies race condition
public Reservation makeReservationVulnerable(ReservationRequest request) {
    Slot slot = slotRepository.findById(id).orElseThrow(...);

    if (slot.getReservedCount() >= slot.getCapacity()) {
        throw new IllegalStateException("Slot capacity exceeded!");
    }

    // Artificial 15ms delay simulates real-world context switch
    Thread.sleep(15);

    slot.setReservedCount(slot.getReservedCount() + 1);
    reservationRepository.save(reservation);
    slotRepository.save(slot);
}
```

Under 20 concurrent requests to a `capacity=1` slot, this will produce 2+ successful reservations.

---

## SAFE Implementation — Pessimistic Locking

```java
// SAFE: SELECT FOR UPDATE — blocks other transactions until this one commits
@Transactional(isolation = Isolation.READ_COMMITTED)
public Reservation makeReservationSafe(ReservationRequest request) {
    // findByIdWithPessimisticLock uses @Lock(LockModeType.PESSIMISTIC_WRITE)
    // This translates to: SELECT * FROM slots WHERE id = ? FOR UPDATE
    Slot slot = slotRepository.findByIdWithPessimisticLock(id).orElseThrow(...);

    if (slot.getReservedCount() >= slot.getCapacity()) {
        throw new IllegalStateException("Slot capacity exceeded!");
    }

    // Only this transaction can read/write this row until it commits
    slot.setReservedCount(slot.getReservedCount() + 1);
    slotRepository.save(slot);
    reservationRepository.save(reservation);
    // Lock released on commit
}
```

Now the sequence is serialised:
```
Thread A: SELECT ... FOR UPDATE (acquires lock)
Thread B: SELECT ... FOR UPDATE (blocks — waits for Thread A)
Thread A: reserved_count = 0 < 1, books slot, commits → lock released
Thread B: (now reads) reserved_count = 1 >= 1, throws 409 → rejected
```

---

## Alternative Approaches (documented for interviews)

### Option 2: Optimistic Locking (JPA `@Version`)
```java
@Version
private Long version;

// On concurrent updates, JPA throws OptimisticLockException
// which the service catches and converts to a 409 response
```

**Trade-off:** Lower contention, but requires retry logic on `OptimisticLockException`.

### Option 3: Atomic SQL Operation
```sql
-- Single atomic UPDATE — no separate read needed
UPDATE slots
SET reserved_count = reserved_count + 1
WHERE id = ? AND reserved_count < capacity
-- Returns 0 rows affected if already full → caller can detect and return 409
```

SlotGuard's `SlotRepository` includes this method as `incrementReservedCountAtomic()` — the most performant approach.

---

## How the Test Harness Detects Defects

### API Layer (REST Assured)
```java
CountDownLatch startLatch = new CountDownLatch(1);
// Fire all threads at the exact same moment
startLatch.countDown();

// Count HTTP 201 responses
assertEquals(1, success201Count); // Must be exactly 1 for capacity=1
```

### Database Layer (SQL Invariant)
```java
// After the concurrent blast, query the actual DB rows
SELECT COUNT(*) FROM reservations WHERE slot_id = ?;

// Must be <= capacity
Assert.assertTrue(actualDbCount <= capacity,
    "DOUBLE BOOKING DEFECT: DB has more reservations than capacity!");
```

### API Invariant Endpoint
```
GET /api/slots/{id}/invariant
→ {"data": {"doubleBooked": false, "status": "INVARIANT_HOLDING"}}
```

---

## Before / After Fix Demonstration

This is the most powerful interview story:

### Before Fix (VULNERABLE mode)
```
100 concurrent requests to Slot #1 (capacity=1)
→ HTTP 201: 2      ← DOUBLE BOOKING!
→ HTTP 409: 98
→ DB rows:  2      ← INVARIANT VIOLATED
```

### After Fix (SAFE mode with Pessimistic Lock)
```
100 concurrent requests to Slot #1 (capacity=1)
→ HTTP 201: 1      ← CORRECT!
→ HTTP 409: 99
→ DB rows:  1      ← INVARIANT HOLDING
```

The test framework detects both states automatically. This is the complete concurrency test loop.

---

## Switching Modes

Via the Web UI: click "Switch Concurrency Mode" button.

Via REST API:
```bash
# Switch to VULNERABLE (exposes race condition)
curl -X POST http://localhost:8080/api/config/concurrency-mode \
  -H "Content-Type: application/json" \
  -d '{"mode":"VULNERABLE"}'

# Switch to SAFE (pessimistic lock protection)
curl -X POST http://localhost:8080/api/config/concurrency-mode \
  -H "Content-Type: application/json" \
  -d '{"mode":"SAFE"}'
```

Via automation tests:
```java
// ConcurrentReservationTest uses setConcurrencyMode() before each scenario
setConcurrencyMode("SAFE");
setConcurrencyMode("VULNERABLE");
```
