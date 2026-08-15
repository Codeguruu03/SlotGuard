# SlotGuard — Concurrent Reservation & Double-Booking Prevention Platform

<div align="center">

**Java | Selenium | Playwright | REST Assured | TestNG | JMeter | SQL | Docker | GitLab CI/CD | Allure**

*An automated test engineering platform that detects race conditions and double-booking defects in concurrent reservation systems.*

</div>

---

## What is SlotGuard?

SlotGuard answers one question:

> **Can a reservation system guarantee that a slot's booking count never exceeds its configured capacity — even when thousands of users try to book it simultaneously?**

This is not a generic booking application. It is a **test engineering platform** built specifically around the problem of concurrent reservation correctness. The application is intentionally minimal; the engineering investment is in the testing infrastructure.

---

## The Problem SlotGuard Tests

When 100 users try to book the same slot (capacity = 1) at exactly the same time:

```
User 1 ──────┐
User 2 ──────┤
User 3 ──────┤──→  POST /api/reservations  (Slot ID: 1, capacity: 1)
User 4 ──────┤
...          │
User 100 ────┘
```

**Expected behaviour:**
```
HTTP 201 (Success):  1
HTTP 409 (Rejected): 99
DB Reservation rows: 1
```

**Without proper concurrency control (race condition):**
```
HTTP 201 (Success):  2+  ← DOUBLE BOOKING DEFECT
DB Reservation rows: 2+  ← INVARIANT VIOLATED
```

SlotGuard's test suite detects this automatically, at every level.

---

## Quick Start

### Prerequisites
- Java 17+
- Maven 3.8+
- Docker (optional, for full environment)

### Option 1: Local (No Docker)
```bash
# 1. Start the SlotGuard API (H2 in-memory, zero config)
cd application
mvn spring-boot:run

# 2. Open the Web UI
# http://localhost:8080

# 3. Run the full automation suite (in another terminal)
cd automation
mvn test
```

### Option 2: Docker (Zero Config)
```bash
# Start the API only
docker compose -f docker/docker-compose.yml up --build

# Start API + run all automation tests
docker compose -f docker/docker-compose.yml --profile tests up --build
```

### Generate Allure Report
```bash
cd automation
mvn allure:serve   # Opens interactive report in browser
```

---

## Project Structure

```
slotguard/
│
├── application/                  # Spring Boot REST API
│   ├── src/main/java/com/slotguard/application/
│   │   ├── controller/           # SlotController, ReservationController, ConfigController
│   │   ├── service/              # ReservationService (SAFE + VULNERABLE modes)
│   │   ├── model/                # Slot, Reservation, ConcurrencyMode
│   │   ├── repository/           # JPA + pessimistic lock query
│   │   ├── dto/                  # Request/Response DTOs
│   │   └── exception/            # GlobalExceptionHandler (409 on overbooking)
│   ├── src/main/resources/static/index.html  # Web Dashboard UI
│   ├── Dockerfile
│   └── pom.xml
│
├── automation/                   # Complete test engineering suite
│   └── src/test/java/com/slotguard/automation/
│       ├── api/                  # SlotApiTest, ReservationApiTest (REST Assured)
│       ├── concurrency/          # ConcurrentReservationTest ← THE KEY TEST
│       ├── database/             # ReservationDatabaseTest (SQL invariant)
│       ├── ui/                   # SeleniumReservationTest, PlaywrightReservationTest
│       └── config/               # TestConfig
│   ├── testng.xml
│   └── pom.xml
│
├── performance/
│   └── slotguard-load-test.jmx  # JMeter 100-1000 user load test
│
├── database/
│   ├── schema.sql                # PostgreSQL schema + slot_invariants_view
│   └── test-data.sql             # Seed data
│
├── docker/
│   └── docker-compose.yml        # Full environment (API + test runner)
│
├── docs/
│   ├── architecture.md
│   ├── test-strategy.md
│   └── concurrency-strategy.md
│
└── .gitlab-ci.yml                # 6-stage CI/CD pipeline
```

---

## Test Suite

| Test Type | Tool | Group | What it Tests |
|---|---|---|---|
| Unit | JUnit 5 + MockMvc | — | Controller + service layer in isolation |
| API | REST Assured | `api` | POST/GET slots and reservations |
| **Concurrency** | REST Assured + Threads | `concurrency` | **Race condition detection** |
| Database | JDBC + SQL | `database` | DB-level invariant: `reservations <= capacity` |
| UI | Selenium (Chrome) | `ui` | End-to-end booking workflow via browser |
| Cross-Browser | Playwright | `cross-browser` | Same workflow on Chromium, Firefox, WebKit |
| Load | JMeter | — | 10 / 100 / 1000 concurrent users |

### Running Specific Test Groups
```bash
cd automation

# Smoke only (quick sanity)
mvn test -Dgroups=smoke

# Concurrency tests only (the core)
mvn test -Dgroups=concurrency

# API tests only
mvn test -Dgroups=api

# All regression tests
mvn test -Dgroups="regression,concurrency,database"
```

---

## Concurrency Demo

The application ships with a built-in **dual-mode concurrency switch**:

| Mode | Description | Race Condition |
|---|---|---|
| `VULNERABLE` | No locking; artificial 15ms delay amplifies race condition | ✅ Exposed |
| `SAFE` | `SELECT FOR UPDATE` (pessimistic lock) inside a transaction | ❌ Prevented |

**Switch via Web UI:** Click the "Switch Concurrency Mode" button on the dashboard.

**Switch via API:**
```bash
curl -X POST http://localhost:8080/api/config/concurrency-mode \
  -H "Content-Type: application/json" \
  -d '{"mode":"VULNERABLE"}'
```

**Browser stress test:** Use the "Browser Concurrent Request Blast" panel on the dashboard to fire 10/50/100 simultaneous requests from your browser.

---

## Key API Endpoints

| Method | Endpoint | Description |
|---|---|---|
| `GET` | `/api/slots` | List all slots |
| `POST` | `/api/slots` | Create a slot |
| `GET` | `/api/slots/{id}/invariant` | **DB invariant check** — `INVARIANT_HOLDING` or `DOUBLE_BOOKED` |
| `POST` | `/api/reservations` | Make a reservation (201 or 409) |
| `GET` | `/api/reservations/slot/{slotId}` | List reservations for a slot |
| `DELETE` | `/api/reservations/{id}` | Cancel reservation |
| `GET` | `/api/config/concurrency-mode` | Get current mode |
| `POST` | `/api/config/concurrency-mode` | Switch mode |

---

## CI/CD Pipeline (GitLab)

```
Git Push
   │
   ▼
Build (Maven package)
   │
   ▼
Unit Tests (JUnit + MockMvc)
   │
   ▼
Integration Tests (API + Concurrency + DB)
   │
   ▼
UI Tests (Selenium + Playwright)
   │
   ▼
Load Tests (JMeter — 100 concurrent users)
   │
   ▼
Allure Report (published to GitLab Pages)
```

---

## Allure Report Sample

```
╔══════════════════════════════════════╗
║      SLOTGUARD TEST REPORT           ║
╠══════════════════════════════════════╣
║  Requests             100            ║
║  Successful (201)     1              ║
║  Rejected (409)       99             ║
║  Double Bookings      0              ║
║  DB Violations        0              ║
║  Avg Response         142 ms         ║
║  P95 Response         391 ms         ║
║  Status               PASSED ✅      ║
╚══════════════════════════════════════╝
```

---

## Technology Stack

| Technology | Why It's Here |
|---|---|
| Java 17 | Automation + application language |
| Spring Boot 3 | REST API + transaction management |
| PostgreSQL / H2 | Persistent / in-memory DB with full ACID guarantees |
| JPA Pessimistic Lock | `SELECT FOR UPDATE` — the SAFE fix |
| Selenium | Functional UI reservation testing |
| Playwright | Cross-browser: Chromium, Firefox, WebKit |
| REST Assured | REST API automation (readable DSL) |
| TestNG | Test orchestration, groups, parallel execution, DataProvider |
| JMeter | Concurrent load testing (race condition at scale) |
| SQL + JDBC | Database-level invariant assertion |
| Docker + Compose | Reproducible test environment (clone → `docker compose up` → test) |
| GitLab CI/CD | Automated 6-stage test pipeline |
| Allure | Test report: what ran, what passed, concurrency metrics |

---

## Resume Entry

> **SlotGuard — Concurrent Reservation Test Engineering Platform**
> Java | Selenium | Playwright | REST Assured | TestNG | JMeter | SQL | Docker | GitLab CI/CD | Allure
>
> - Developed an automated test engineering platform for detecting race conditions and double-booking defects in concurrent reservation systems.
> - Built API, UI, database, cross-browser, and 100+ concurrent-user load tests using REST Assured, Selenium, Playwright, TestNG, and JMeter.
> - Implemented automated database invariant validation using SQL to verify that successful reservations never exceeded slot capacity.
> - Containerized the application and test environment with Docker; integrated automated regression, concurrency, and performance testing into GitLab CI/CD.
> - Generated Allure test reports and integrated failed test scenarios with Jira for automated defect tracking.
