# 📋 Project Roadmap & Master TODO — Payments Processing System

> **Rule of engagement:** We go ONE stage at a time. A stage is not "done" until reviewed & explicitly confirmed. This file is the single source of truth for progress. Check items off as we go — do not jump ahead.

**Deadline:** Thursday, **6 Aug 2026**
**Today:** 05 Aug 2026
**Time available:** ~6 working days → we MUST stay lean, agile, and ruthlessly prioritize the CORE lifecycle first.

> **31 Jul 2026 update:** First customer meeting held. New scope confirmed: `Account` entity (multi-account source picker), INR/USD-only currencies, KPI dashboard + basic analytics, ~40k req/min rate limiting, elevated concurrency/durability/reliability, and API security hardening. All design docs (Phases 0–4) have been revised accordingly — see `01-CONTEXT.md` §9 and `02-MEMORY.md` MEM-017–022. **No implementation code has been written yet, so this is a documentation-only revision, not a rework.**

> **05 Aug 2026 update — Core (MVP) COMPLETE, V2 kicked off:** Features #1–#12 (`chirag/01-feature-list.md`) are implemented and shippable. The team now moves to **V2**: the "Good to Have" (#13–#17) and select "Future" (#18–#20) features, plus wiring the already-built `notification` module (#21) and replacing the dashboard's 30s poll with SSE. See `02-MEMORY.md` MEM-023–034 and the new `docs/13-WORK-DISTRIBUTION-V2.md` for full detail. Phases 0–8 below (Core build) are preserved as-is for history; **Phase 9 (new, below)** covers the V2 wave.

---

## 🧭 Phase 0 — Groundwork (Day 1 — TODAY)

- [x] Read & digest problem statement (`payment_processing.md`)
- [x] `docs/00-ROADMAP.md` (this file) — master plan & to-do tracker
- [x] `docs/01-CONTEXT.md` — project context (scope, stakeholders, constraints)
- [x] `docs/02-MEMORY.md` — decision log (ADR-lite) — updated every time we decide something
- [x] `docs/03-SKILLS.md` — tech stack, team skill assumptions, standards we'll follow
- [ ] **Gate:** You review these 4 files → confirm → we proceed to Phase 1

## 📐 Phase 1 — Requirements & Design Foundations

- [x] `docs/04-SRS.md` — Software Requirements Specification (lightweight, agile-style — not a 100-page waterfall doc)
  - Functional requirements
  - Non-functional requirements (NFRs)
  - Minimum data model (per checklist: id, amount, currency, status — then grow)
  - Constraints & assumptions
  - Out-of-scope (explicitly, to protect the deadline)
- [x] **Gate:** Confirm SRS → proceed (defaults locked via MEM-006/007; 1 low-risk open item on account-existence validation, non-blocking)

## 🏗️ Phase 2 — Architecture & Design Patterns

- [x] `docs/05-ARCHITECTURE.md` — chosen architecture style (layered/hexagonal), justification
- [x] `docs/06-DESIGN-PATTERNS.md` — pattern selection with rationale, e.g.:
  - **State Pattern** (or Spring Statemachine) for payment lifecycle transitions
  - **Strategy Pattern** for validation rules (pluggable validators)
  - **Repository Pattern** (Spring Data JPA) for persistence
  - **DTO + Mapper (MapStruct)** pattern for API boundary
  - **Builder Pattern** for Payment entity construction
  - **Chain of Responsibility** for validation pipeline
  - **Idempotency Filter / Interceptor** pattern
  - **Outbox/Event pattern (optional, stretch)** for audit trail & async processing
- [x] **Gate:** Confirm design pattern choices → proceed (logged as MEM-008/009/010)
- [x] `docs/07-TESTING-STRATEGY.md` — test plan across ALL layers (unit/business logic, repository `@DataJpaTest`, web-layer `@WebMvcTest`+MockMvc per route, integration `@SpringBootTest`), mapped to Appendix F scenarios, with per-member ownership and coverage expectations

## 🧩 Phase 3 — UML & Diagrams

- [x] `docs/08-UML-CLASS-DIAGRAM.md` (+ Mermaid/PlantUML source) — Class Diagram
- [x] `docs/09-UML-SEQUENCE-DIAGRAMS.md` — Sequence diagrams for:
  - Create Payment (incl. idempotency check)
  - Validate → Send → Complete happy path
  - Failure path (any stage → FAILED)
  - Get Payment / History query
- [x] `docs/10-UML-STATE-DIAGRAM.md` — Payment status state machine diagram
- [x] **Gate:** Confirm diagrams → proceed

## 📖 Phase 4 — API Design Docs ("Storybook") — enables Frontend to work in PARALLEL

- [x] `docs/11-API-DESIGN.md` — Full REST contract:
  - Endpoints, methods, request/response JSON, status codes, error codes (Appendix B mapping)
  - Idempotency-Key header behavior
  - Pagination/filtering for list & history endpoints
- [x] OpenAPI/Swagger spec (`docs/openapi.yaml`) generated from above — frontend team can mock against this immediately (e.g. via Prism/MSW) without waiting for backend
- [x] **Gate:** Confirm API contract → proceed (logged as MEM-013, MEM-014)
- [x] `docs/12-CLARIFICATION-QUESTIONS.md` — consolidated list of questions to ask the instructor, prioritized (High/Medium/Low), each with our current default so we're not blocked
- [x] `docs/13-WORK-DISTRIBUTION.md` — dedicated day-by-day work breakdown for the team of 4, module ownership, dependencies, Git workflow rules

## 🔨 Phase 5 — Core Implementation (Backend: Spring Boot)

- [ ] Sprint 0: Project skeleton (Maven/Gradle, package structure, Git repo, CI skeleton) — **M1**
- [ ] Sprint 1 (CORE — MUST HAVE by mid-week) — all 4 members, one vertical module each (see `docs/03-SKILLS.md` §3):
  - **M1:** `Payment` entity (minimal fields) + Flyway/MySQL migration + idempotency key handling (unique constraint + lookup) + **`Account` entity/migration/seed data + `AccountRepository`** (new, MEM-017)
  - **M2:** Status transition engine (State pattern) + validation of transitions + `payment_status_history` audit table + history endpoint
  - **M3:** Create Payment endpoint + request validation (Bean Validation) + global exception handling + error code contract (Appendix B) + `AccountValidator`/`CurrencyValidator` updated for `sourceAccountId`/INR-USD (MEM-017/018)
  - **M4:** Get Payment by ID + List Payments endpoint + Swagger/OpenAPI setup (`docs/openapi.yaml`) + **`GET /accounts` endpoints** — becomes the contract M3/M4 hand to frontend later
  - Unit + integration tests for happy path & key edge cases — each member owns tests for their module
- [ ] Sprint 1.5 (NEW cross-cutting, MEM-019/020/021/022 — target Day 4–5): `RateLimitFilter` (Bucket4j), Resilience4j Circuit Breaker/Retry around simulated send/complete, `AnalyticsService` + `GET /analytics/summary|trend`, security headers/CORS lock-down — shared across the team, whoever has bandwidth first
- [ ] Sprint 2 (enhance, Day 5+) — **M1 + M2 (backend)**: validation rule chain (Strategy pattern), search/filter by status, retry/simulated async processing, test hardening, load-test pass (§ testing strategy 2.6)
- [ ] Sprint 3 (stretch, Appendix E): pick 1–2 advanced features if time allows — M1/M2 if backend is ahead of schedule

## 🎨 Phase 6 — Frontend (React) — **M3 + M4**, starts Day 5 once API design is locked (Phase 4)

- [ ] React app skeleton (Vite + TS) — **M4**
- [ ] **KPI Dashboard screen (landing page, per customer directive)** — **M4** (new, MEM-019)
- [ ] Create Payment screen — **including source-account dropdown fed by `GET /accounts`** — **M3** (updated, MEM-017)
- [ ] Payment Details + status history timeline — **M3**
- [ ] Payment List + filter/search — **M4**
- [ ] Error display for failed payments (incl. graceful `429` handling) — **M4** (updated, MEM-020)

## 🧪 Phase 7 — Testing, Hardening, Docs polish

- [ ] Test coverage review (unit/integration)
- [ ] Edge case pass: duplicates, invalid transitions, concurrency
- [ ] README polish, architecture diagram export, demo script

## 🎤 Phase 8 — Presentation Prep

- [ ] Slide outline per the "Presentation Guidelines" in spec
- [ ] Live demo script & rehearsal
- [ ] Q&A prep

---

## 🗓️ Suggested Day-by-Day (7 days to deadline)

| Day | Date | Focus |
|---|---|---|
| 1 | Jul 30 | Groundwork docs + SRS + Design pattern decisions |
| 2 | Jul 31 (today) | First customer meeting → docs revised (Account, KPIs, INR/USD, rate limiting, security, concurrency) + UML (class, sequence, state) + API design/OpenAPI, all updated |
| 3 | Aug 1 | Backend skeleton + DB schema (incl. `account` table) + Create/Get Payment + Account endpoints |
| 4 | Aug 2 | Status transition engine + audit trail + error handling + RateLimitFilter + Circuit Breaker wiring |
| 5 | Aug 3 | Search/filter, Analytics endpoints, tests, Swagger polish, load-test pass; **Frontend starts in parallel** |
| 6 | Aug 4 | Frontend screens wired to API (Dashboard first!); backend hardening/tests |
| 7 | Aug 5 | Integration pass, bug fixes, stretch features if time, demo rehearsal |
| — | Aug 6 | **Presentation day** |

---

### ✅ Current Status
**Phase 0 ✅ | Phase 1 (SRS) ✅ | Phase 2 (Architecture, Patterns & Testing Strategy) ✅ | Phase 3 (UML) ✅ | Phase 4 (API Design/OpenAPI) ✅ | Phase 5–8 (Core Implementation, Frontend, Testing, Presentation Prep) ✅ COMPLETE — Features #1–#12 shipped. We are now on: Phase 9 — V2 Enhancement Wave (see below).**

> ⏰ **Day check:** Today is 05 Aug 2026 (Day 7, 1 day before the presentation). Core MVP (Phases 0–8) is complete and demo-ready. Phase 9 (V2) covers additional Good-to-Have/Future features requested for this final push — see `13-WORK-DISTRIBUTION-V2.md` for the day-of breakdown against the 06 Aug deadline.

---

## 🚀 Phase 9 — V2 Enhancement Wave (05 Aug 2026 → deadline)

> Full breakdown, ownership, and dependency notes: **`docs/13-WORK-DISTRIBUTION-V2.md`**. Decisions/rationale: `02-MEMORY.md` MEM-023–034. This phase follows the same gated discipline as Phases 0–8 — docs updated first, code follows.

- [ ] **M1 — Notifications (#21) wiring:** `PaymentNotificationListener` (`@EventListener`, async) subscribed to `PaymentStatusChangedEvent`; `Account.owner_email`/`owner_name` migration (`V6__add_account_owner_contact.sql`); triggers `notifyPaymentCreated/Completed/Failed`.
- [ ] **M1 — Sortable Columns (#16):** clickable column headers on `payments.html` (Amount/Status/Created), `sort` allow-list validation on `GET /payments`.
- [ ] **M1 — Load Test v2:** extend `load-tests/` to cover cancellation/reversal/export endpoints alongside the existing create-payment burst scenario.
- [ ] **M1 — Dashboard SSE:** `GET /analytics/stream` (`SseEmitter`), `DashboardStreamService`, frontend `EventSource` replacing `setInterval(loadDashboard, 30000)` in `frontend/index.html` (poll retained only as a fallback).
- [ ] **M2 — Create Payment frontend cross-check:** audit `frontend/js/api.js`/`create-payment.html` against `04-SRS.md`/`11-API-DESIGN.md`; fix the known `API_BASE` hardcoding issue (MEM-028) as the first task.
- [ ] **M2 — Payment Cancellation (#18):** new `CANCELLED` terminal status + `CancelledState`, `POST /payments/{id}/cancel` (legal only from `CREATED`).
- [ ] **M2 — Payment Reversal (#19):** `POST /payments/{id}/reverse` (legal only from `COMPLETED`), creates a linked, direction-swapped payment; `reversed` flag + `reversal_payment_id`/`reversal_of_payment_id` columns.
- [x] **M3 — Analytics/Trend View (#13), deepened:** ✅ Implemented 06 Aug — configurable window (24h/48h/7d) + per-currency volume breakdown per bucket, capped via `paypulse.analytics.trend.max-hours`. See MEM-035.
- [x] **M3 — CSV Export (#14):** ✅ Implemented 06 Aug — `GET /payments/export`, batch-streamed, same filters as `GET /payments`, row-count cap (`EXPORT_TOO_LARGE`), two-phase validate-then-stream to avoid corrupting committed response headers. See MEM-035.
- [ ] **M4 — Copy Payment ID / Deep Linking (#17):** copy-to-clipboard affordance; formalize `payment-details.html?id=` and `payments.html?status=` as documented stable deep-link contracts.
- [ ] **M4 — Multi-Currency Conversion (#20), display-only:** `GET /fx/rate`, static config-driven `FxRateService`, frontend "≈ other currency" display hint (no real conversion/settlement).
- [ ] **Gate:** All 4 verticals reviewed + merged + `openapi.yaml`/docs updated → V2 demo-ready before the 06 Aug presentation.

