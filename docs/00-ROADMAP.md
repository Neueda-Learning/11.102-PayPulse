# 📋 Project Roadmap & Master TODO — Payments Processing System

> **Rule of engagement:** We go ONE stage at a time. A stage is not "done" until reviewed & explicitly confirmed. This file is the single source of truth for progress. Check items off as we go — do not jump ahead.

**Deadline:** Thursday, **6 Aug 2026**
**Today:** 31 Jul 2026
**Time available:** ~6 working days → we MUST stay lean, agile, and ruthlessly prioritize the CORE lifecycle first.

> **31 Jul 2026 update:** First customer meeting held. New scope confirmed: `Account` entity (multi-account source picker), INR/USD-only currencies, KPI dashboard + basic analytics, ~40k req/min rate limiting, elevated concurrency/durability/reliability, and API security hardening. All design docs (Phases 0–4) have been revised accordingly — see `01-CONTEXT.md` §9 and `02-MEMORY.md` MEM-017–022. **No implementation code has been written yet, so this is a documentation-only revision, not a rework.**

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
**Phase 0 ✅ | Phase 1 (SRS) ✅ Revised 31 Jul | Phase 2 (Architecture, Patterns & Testing Strategy) ✅ Revised 31 Jul | Phase 3 (UML) ✅ Revised 31 Jul | Phase 4 (API Design/OpenAPI) ✅ Revised 31 Jul | We are now on: Phase 5 — Core Implementation (Backend Sprint 0/1), incorporating the customer-meeting scope update.**

> ⏰ **Day check:** Today is Jul 31 (Day 2). First customer meeting completed same day; all design/documentation phases (0–4) revised in response before any implementation code was written — zero throwaway code, pure documentation update. Sprint 0 (project skeleton) begins Day 3 as planned, now against the updated contract.
