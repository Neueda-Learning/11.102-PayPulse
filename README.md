# 💳 PayPulse — Payments Processing System

> **Quick start:** 3 commands and the full stack is running. See [§ Running with Docker](#-running-with-docker) below.

---

## Table of Contents

1. [What is PayPulse?](#what-is-paypulse)
2. [Architecture at a Glance](#architecture-at-a-glance)
3. [Prerequisites](#prerequisites)
4. [Running with Docker](#-running-with-docker) ← **start here**
5. [Verifying Everything Works](#verifying-everything-works)
6. [Seed Data (automatic)](#seed-data-automatic)
7. [Ports & URLs Reference](#ports--urls-reference)
8. [Stopping & Resetting](#stopping--resetting)
9. [Local Development Without Docker](#local-development-without-docker-optional)
10. [Project Structure](#project-structure)
11. [Team Work Distribution](#team-work-distribution)
12. [Troubleshooting](#troubleshooting)

---

## What is PayPulse?

PayPulse is a payments processing platform that manages the full lifecycle of financial payments — from creation through validation, transmission, and completion — with a complete audit trail and a KPI dashboard.

**Tech stack:**
| Layer | Technology |
|---|---|
| Backend API | Spring Boot 3.3, Java 17, Maven |
| Database | MySQL 8.0 (schema managed by Flyway) |
| Frontend | Plain HTML + CSS + JavaScript (served by nginx) |
| Containers | Docker + Docker Compose (3 containers) |

---

## Architecture at a Glance

```
Browser
  │
  ▼
┌──────────────────────────────┐  port 3000
│  frontend container (nginx)  │
│  - serves HTML/CSS/JS        │
│  - proxies /api/* ──────────────────────────────────┐
└──────────────────────────────┘                      │
                                                      ▼
                                        ┌─────────────────────────┐  port 8080
                                        │  backend container      │
                                        │  Spring Boot API        │
                                        │  Flyway migrations      │
                                        │  + seed data on start   │
                                        └────────────┬────────────┘
                                                     │
                                                     ▼
                                        ┌─────────────────────────┐  port 3306
                                        │  mysql container        │
                                        │  MySQL 8.0              │
                                        │  database: paypulse     │
                                        └─────────────────────────┘
```

The nginx proxy is the key piece — your browser only ever talks to `localhost:3000`, and nginx forwards `/api/*` calls to the backend container internally. No CORS issues, no hardcoded backend URLs in the frontend JavaScript.

---

## Prerequisites

Install these once. That's all you need.

| Tool | Version | Download |
|---|---|---|
| **Docker Desktop** | 4.x or newer | https://www.docker.com/products/docker-desktop |
| **Docker Compose** | v2 (bundled with Docker Desktop) | included above |

> **No Java, no Maven, no MySQL, no Node.js needed on your machine.** Everything runs inside containers.

Verify your install:
```bash
docker --version        # should print Docker version 24.x or newer
docker compose version  # should print Docker Compose version v2.x
```

---

## 🚀 Running with Docker

### Step 1 — Clone the repository (if not already done)

```bash
git clone <repository-url>
cd 102-Payment-Processing-System-PayPulse
```

### Step 2 — Start all three containers

```bash
docker compose up --build
```

> The `--build` flag builds the backend JAR and the nginx image from scratch.
> On first run this takes **2–4 minutes** (Maven downloads dependencies, Docker builds images).
> Subsequent runs with `docker compose up` (no `--build`) start in **~15 seconds**.

**What you will see in the terminal:**

```
[+] Building backend image...    ← Maven compiles the Spring Boot JAR inside Docker
[+] Building frontend image...   ← nginx image with your HTML/CSS/JS
[+] Creating mysql container...
[+] Creating backend container...  ← Flyway runs migrations + seeds 3 accounts automatically
[+] Creating frontend container...
paypulse-backend  | Started PaymentsApplication in 4.2 seconds
paypulse-mysql    | ready for connections
```

Wait until you see `Started PaymentsApplication` — that means migrations have run and the API is ready.

### Step 3 — Open the app

Open your browser and go to: **http://localhost:3000**

You will land on the **Dashboard** (KPI screen). It will show an error until you create some payments — that's expected, the analytics queries return zeros on an empty database.

---

## Verifying Everything Works

Run through this checklist after `docker compose up --build`:

### ✅ Check 1 — All containers are running

```bash
docker compose ps
```

Expected output:
```
NAME                 STATUS          PORTS
paypulse-mysql       Up (healthy)    0.0.0.0:3306->3306/tcp
paypulse-backend     Up (healthy)    0.0.0.0:8080->8080/tcp
paypulse-frontend    Up              0.0.0.0:3000->80/tcp
```

All three should show **Up** (mysql and backend should show **healthy** once their health checks pass).

### ✅ Check 2 — Backend health endpoint

```bash
curl http://localhost:8080/actuator/health
```
or open http://localhost:8080/actuator/health in your browser.

Expected: `{"status":"UP"}`

### ✅ Check 3 — Seed accounts are loaded

```bash
curl http://localhost:8080/api/v1/accounts
```

Expected — 3 accounts (seeded automatically by Flyway `V3__seed_accounts.sql`):
```json
[
  {"id":"b2c3d4e5-1111-4a11-8a11-111111111111","label":"Primary INR Savings","accountNumber":"ACC1000001","currency":"INR","status":"ACTIVE"},
  {"id":"c3d4e5f6-2222-4a22-8a22-222222222222","label":"USD Wallet","accountNumber":"ACC2000002","currency":"USD","status":"ACTIVE"},
  {"id":"d4e5f6a7-3333-4a33-8a33-333333333333","label":"Old INR Account","accountNumber":"ACC3000003","currency":"INR","status":"INACTIVE"}
]
```

### ✅ Check 4 — Create a test payment

```bash
curl -X POST http://localhost:8080/api/v1/payments \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: test-001" \
  -d '{
    "sourceAccountId": "b2c3d4e5-1111-4a11-8a11-111111111111",
    "amount": 500.00,
    "currency": "INR",
    "destinationAccount": "ACC9998887",
    "reference": "Test payment"
  }'
```

Expected: `201 Created` with a `PaymentResponse` body showing `"status": "COMPLETED"` (auto-processed).

### ✅ Check 5 — Frontend is working

Open http://localhost:3000/accounts.html — you should see the 3 seed accounts.

Open http://localhost:3000/create-payment.html — the source account dropdown should be populated.

### ✅ Check 6 — Swagger UI

Open http://localhost:3000/swagger-ui.html — interactive API docs. You can try all endpoints directly from the browser.

---

## Seed Data (Automatic)

**You do not need to run any scripts.** When the backend container starts, Flyway automatically runs all SQL migrations in order:

| Migration | What it does |
|---|---|
| `V1__init_payment_schema.sql` | Creates `payment` and `payment_status_history` tables |
| `V2__create_account_table.sql` | Creates `account` table, adds FK from payment → account |
| `V3__seed_accounts.sql` | Inserts 3 seed accounts (2 ACTIVE, 1 INACTIVE) |

The seed accounts are:

| Account | Number | Currency | Status | Use for |
|---|---|---|---|---|
| Primary INR Savings | ACC1000001 | INR | **ACTIVE** | Normal INR payments |
| USD Wallet | ACC2000002 | USD | **ACTIVE** | Normal USD payments |
| Old INR Account | ACC3000003 | INR | INACTIVE | Testing rejection of inactive account |

> **Flyway is idempotent** — if you restart the containers, migrations only run if the database is empty or the migration hasn't been applied yet. Your existing payment data is safe.

### Reset seed data from scratch

Only do this if you want to wipe all data and start fresh:

```bash
docker compose down -v   # -v removes the mysql_data volume (DELETES ALL DATA)
docker compose up --build
```

---

## Ports & URLs Reference

| What | URL | Notes |
|---|---|---|
| **Frontend (app)** | http://localhost:3000 | Start here |
| **Dashboard** | http://localhost:3000/index.html | KPI screen |
| **Accounts** | http://localhost:3000/accounts.html | View seed accounts |
| **Create Payment** | http://localhost:3000/create-payment.html | Submit a payment |
| **Payment List** | http://localhost:3000/payments.html | Browse all payments |
| **Swagger UI** | http://localhost:3000/swagger-ui.html | Interactive API docs |
| **Backend API direct** | http://localhost:8080/api/v1 | Dev/debugging only |
| **Backend health** | http://localhost:8080/actuator/health | Health check |
| **MySQL** | localhost:3306 | DB: `paypulse`, User: `root`, Pass: `n3u3da!` |

---

## Stopping & Resetting

```bash
# Stop containers (keeps your data — resume with docker compose up)
docker compose stop

# Stop and remove containers (keeps your mysql_data volume — data intact)
docker compose down

# Stop, remove containers AND wipe all database data (full reset)
docker compose down -v

# Rebuild images after a code change (e.g. after adding a new endpoint)
docker compose up --build
```

---

## Local Development Without Docker (Optional)

If you want to run the backend directly in your IDE for faster feedback:

### Prerequisites for local dev

- Java 17 (JDK) — https://adoptium.net
- Maven 3.9+ — https://maven.apache.org (or use the Maven wrapper `./mvnw`)
- MySQL 8.0 running locally on port 3306 with database `paypulse`, user `root`, password `n3u3da!`

### Start backend locally

```bash
cd backend
./mvnw spring-boot:run
# or in IntelliJ: right-click PaymentsApplication.java → Run
```

The backend reads `application.yml` which is already configured for `localhost:3306` with `root`/`n3u3da!`.

Flyway will run migrations automatically on startup — same seed data as Docker.

### Start frontend locally

Open `frontend/index.html` directly in your browser.

> **Important:** When running locally (not via Docker), the frontend's `js/api.js` uses `/api/v1/...` as the base URL. Since there's no nginx proxy locally, this will call `localhost:PORT/api/v1/...` which only works if you open the file via a local HTTP server, not `file://`. The easiest option is:

```bash
# In the frontend/ directory, start a simple HTTP server
cd frontend
python -m http.server 3000
# Then open http://localhost:3000
```

Or just use Docker (simpler).

---

## Project Structure

```
102-Payment-Processing-System-PayPulse/
├── docker-compose.yml          ← wire 3 containers together
├── .env                        ← credentials (root / n3u3da!)
├── .gitignore
├── README.md                   ← you are here
│
├── backend/                    ← Spring Boot (Java 17, Maven)
│   ├── Dockerfile
│   ├── pom.xml                 ← all dependencies
│   └── src/main/
│       ├── java/com/paypulse/
│       │   ├── PaymentsApplication.java
│       │   ├── account/        ← M1 entities + M4 controller
│       │   ├── payment/        ← M1 entity, M2 state machine, M3 create, M4 read
│       │   ├── analytics/      ← M4 KPI dashboard
│       │   └── common/         ← error handling, rate limiting, CORS, config
│       └── resources/
│           ├── application.yml
│           ├── application-docker.yml
│           └── db/migration/
│               ├── V1__init_payment_schema.sql
│               ├── V2__create_account_table.sql
│               └── V3__seed_accounts.sql  ← 3 accounts auto-seeded on startup
│
├── frontend/                   ← Plain HTML + CSS + JS (nginx)
│   ├── Dockerfile
│   ├── nginx/nginx.conf        ← proxies /api/* to backend container
│   ├── css/style.css
│   ├── js/api.js               ← all backend API calls in one file
│   ├── index.html              ← Dashboard (KPIs) — landing page
│   ├── accounts.html
│   ├── create-payment.html
│   ├── payments.html
│   └── payment-details.html
│
├── docs/                       ← All design documentation
│   ├── 04-SRS.md               ← Requirements
│   ├── 05-ARCHITECTURE.md      ← Package structure and design decisions
│   ├── 11-API-DESIGN.md        ← REST API contract (human-readable)
│   ├── openapi.yaml            ← Machine-readable API spec (frozen contract)
│   └── 13-WORK-DISTRIBUTION.md ← Who owns what (M1–M4 assignments)
│
└── chirag/                     ← Customer review deliverables
    ├── 01-feature-list.md
    ├── 02-user-stories.md
    ├── 03-acceptance-criteria.md
    └── 04-wireframes/          ← clickable HTML prototype (dummy data — for reference only)
```

---

## Team Work Distribution

Each team member owns a **full-stack vertical slice** (backend + frontend + tests). See `docs/13-WORK-DISTRIBUTION.md` for the complete breakdown.

| Member | Vertical | Main files to edit |
|---|---|---|
| **M1** | Accounts, Idempotency, Currency | `account/domain/`, `payment/domain/Payment.java`, migrations, `AccountPicker` frontend component |
| **M2** | State Machine, Audit Trail, Rate Limiting | `payment/service/states/`, `StatusTransitionEngine`, `RateLimitFilter`, `StatusHistoryTimeline` |
| **M3** | Create Payment, Validation, Security | `PaymentService`, validators, `GlobalExceptionHandler`, `CreatePaymentPage.html` |
| **M4** | Read/List, KPI Dashboard, Analytics | `AccountController`, `AnalyticsController/Service`, `payments.html`, `index.html` |

**Before writing any code:**
1. Read `docs/05-ARCHITECTURE.md` (package structure)
2. Read your section in `docs/13-WORK-DISTRIBUTION.md` (exact files you own)
3. Check `docs/openapi.yaml` — this is the frozen API contract, do not change it without a team discussion

---

## Troubleshooting

### `docker compose up` fails immediately

```bash
# Check Docker Desktop is running
docker info
```

### Backend keeps restarting / MySQL not ready

MySQL takes 20–30 seconds to initialize on first run. The backend has a health-check wait (`depends_on: condition: service_healthy`) so it will retry. Wait for the full output — it should self-heal.

If it still fails:
```bash
docker compose logs mysql    # check MySQL logs
docker compose logs backend  # check Spring Boot logs
```

### "Access denied for user 'root'" error in backend logs

Your `.env` file may have a different password. Check:
```bash
cat .env   # should show MYSQL_ROOT_PASSWORD=n3u3da!
```

If you changed it after first run, MySQL has already initialized with the old password. Reset:
```bash
docker compose down -v   # wipes MySQL data
docker compose up --build
```

### Port 3306 / 8080 / 3000 already in use

Another app is using that port. Find and stop it:
```bash
# Windows
netstat -ano | findstr :3306
netstat -ano | findstr :8080
netstat -ano | findstr :3000
```

Or change the host-side port in `docker-compose.yml` (e.g. `"3307:3306"` for MySQL).

### Frontend shows "Could not load KPIs" / "Could not load accounts"

This means the frontend loaded but can't reach the backend. Check:
1. Is the backend container running? `docker compose ps`
2. Is the backend healthy? `curl http://localhost:8080/actuator/health`
3. Check backend logs: `docker compose logs backend`

The most common cause is the backend is still starting up — wait 30 seconds and refresh.

### Flyway error: "Table already exists" or "Checksum mismatch"

**Never edit a migration file that has already been applied.** Flyway checksums each file. If you need to change the schema, create a new migration file (`V4__your_change.sql`).

If you're on a local dev machine and don't care about the data:
```bash
docker compose down -v   # wipe everything
docker compose up --build
```

### I need to look at the database directly

```bash
# Connect to MySQL inside the container
docker exec -it paypulse-mysql mysql -u root -p"n3u3da!" paypulse

# Useful queries
SHOW TABLES;
SELECT * FROM account;
SELECT id, status, amount, currency FROM payment ORDER BY created_at DESC LIMIT 10;
SELECT * FROM payment_status_history ORDER BY occurred_at DESC LIMIT 20;
```

### Rebuild after a code change

```bash
docker compose up --build
```

Only the changed layer is rebuilt — Docker layer caching means this is fast if you only changed Java source files (not `pom.xml`).

---

## Database Credentials Summary

| Setting | Value |
|---|---|
| Host (from host machine) | `localhost` |
| Host (from backend container) | `mysql` |
| Port | `3306` |
| Database | `paypulse` |


---

> **Design docs** are in `docs/`. Start with `docs/05-ARCHITECTURE.md` to understand the package structure, then `docs/13-WORK-DISTRIBUTION.md` to see your specific assignment.
