# SlotGuard — Architecture

## Overview

SlotGuard is a **concurrent reservation test engineering platform**. Its primary purpose is not to be a fully featured booking system — it exists to answer one question:

> **Can a reservation system guarantee that a slot is never booked more times than its configured capacity, even when thousands of users try simultaneously?**

---

## System Architecture

```
                      SlotGuard Platform
                             │
              ┌──────────────┴──────────────┐
              │                             │
          REST API                       Web UI
    (Spring Boot / Java)           (Vanilla HTML/JS)
              │                             │
         PostgreSQL / H2                Selenium
              │                         Playwright
       Transaction Logic
              │
    Concurrency Protection
    (Pessimistic Lock / SAFE)
    OR
    Race Condition Demo
    (No Lock / VULNERABLE)
```

---

## Application Layers

### Controllers
| Controller | Endpoint Prefix | Purpose |
|---|---|---|
| `SlotController` | `/api/slots` | Create, list, get slots; invariant status |
| `ReservationController` | `/api/reservations` | Make, list, cancel reservations |
| `ConfigController` | `/api/config` | Switch concurrency mode (SAFE / VULNERABLE) |

### Services
| Service | Purpose |
|---|---|
| `SlotService` | CRUD for slots; invariant query |
| `ReservationService` | Reservation logic with SAFE + VULNERABLE dual-mode |
| `ConcurrencyConfigService` | Thread-safe runtime mode switch via `AtomicReference` |

### Models
| Model | Table | Key Fields |
|---|---|---|
| `Slot` | `slots` | `id, title, capacity, reserved_count, version` |
| `Reservation` | `reservations` | `id, slot_id, user_name, status, created_at` |

---

## Concurrency Modes

### VULNERABLE Mode (default)
```java
// Check-then-act without locking or transactional isolation
int available = slot.getReservedCount();
if (available < slot.getCapacity()) {
    Thread.sleep(15); // artificial context switch → exposes race condition
    slot.setReservedCount(available + 1);
    reservationRepository.save(reservation);
}
```

**Effect under 100 concurrent requests:**
```
Expected:   1 success, 99 rejected
Actual:     2+ successes  ← DOUBLE BOOKING DEFECT
```

### SAFE Mode (Pessimistic Locking)
```java
@Transactional(isolation = Isolation.READ_COMMITTED)
public Reservation makeReservationSafe(ReservationRequest request) {
    Slot slot = slotRepository.findByIdWithPessimisticLock(id);
    // SELECT ... FOR UPDATE — no other transaction can read this row
    if (slot.getReservedCount() >= slot.getCapacity()) {
        throw new IllegalStateException("Slot capacity exceeded!");
    }
    slot.setReservedCount(slot.getReservedCount() + 1);
    // ...
}
```

**Effect under 100 concurrent requests:**
```
Expected:   1 success, 99 rejected
Actual:     1 success, 99 rejected  ← INVARIANT HOLDING
```

---

## Database Schema

```sql
CREATE TABLE slots (
    id            BIGSERIAL PRIMARY KEY,
    title         VARCHAR(255) NOT NULL,
    capacity      INT NOT NULL,
    reserved_count INT NOT NULL DEFAULT 0,
    version       BIGINT NOT NULL DEFAULT 0,
    created_at    TIMESTAMP
);

CREATE TABLE reservations (
    id         BIGSERIAL PRIMARY KEY,
    slot_id    BIGINT NOT NULL REFERENCES slots(id) ON DELETE CASCADE,
    user_name  VARCHAR(255) NOT NULL,
    status     VARCHAR(50) NOT NULL DEFAULT 'CONFIRMED',
    created_at TIMESTAMP
);

CREATE INDEX idx_reservations_slot_id ON reservations(slot_id);
```

The `slot_invariants_view` provides a SQL-level invariant check:
```sql
CREATE VIEW slot_invariants_view AS
SELECT s.id, s.capacity, COUNT(r.id) AS actual_db_reservations,
    CASE WHEN COUNT(r.id) <= s.capacity THEN 'INVARIANT_HOLDING' ELSE 'DOUBLE_BOOKED' END
FROM slots s LEFT JOIN reservations r ON s.id = r.slot_id
GROUP BY s.id, s.capacity;
```

---

## Technology Map

| Technology | Specific Role |
|---|---|
| Java 17 | Application runtime + automation language |
| Spring Boot 3 | REST API framework |
| H2 / PostgreSQL | In-memory (dev) / persistent (production) DB |
| JPA + Pessimistic Lock | `SELECT FOR UPDATE` concurrency protection |
| Selenium | Functional UI test automation |
| Playwright | Cross-browser reservation workflow testing |
| REST Assured | HTTP API test automation |
| TestNG | Test orchestration, groups, parallel execution |
| JMeter | Concurrent load testing (100–1000 users) |
| Allure | Rich test reporting with concurrency metrics |
| Docker / Compose | Reproducible test environment |
| GitLab CI/CD | Automated multi-stage test pipeline |
