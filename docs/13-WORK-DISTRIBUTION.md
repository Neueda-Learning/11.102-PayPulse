# 👥 WORK-DISTRIBUTION.md — Team Task Allocation (Team of 4)

Related: `00-ROADMAP.md` (phases/dates), `03-SKILLS.md` §3 (original split summary), `05-ARCHITECTURE.md` (package structure), `07-TESTING-STRATEGY.md` (test ownership).

> This is the **single, authoritative work-breakdown** — treat it like a lightweight Trello board in Markdown. Replace **M1–M4** with real names as soon as assigned. Update checkboxes as work completes (or mirror this into Trello/GitHub Projects per `03-SKILLS.md`).

> **Updated 31 Jul 2026** post-customer-meeting: added Account module (M1), Analytics module (M4), and cross-cutting Rate Limiting/Resilience/Security tasks — MEM-017/019/020/021/022.

---

## 1. Strategy Recap

- **Day 1 (30 Jul):** Done as a team — design docs (Phases 0–4). ✅
- **Day 2 (31 Jul, today):** First customer meeting → all design docs revised same-day (this document included) to add `Account` entity, INR/USD currencies, KPI dashboard/analytics, rate limiting, and security hardening — **before any implementation code exists**, so zero rework cost.
- **Day 3–5 (Sprint 0 + Sprint 1 — CORE, now includes the above additions):** All 4 members on **backend**, each owning one **vertical module** end-to-end (entity/schema → service/logic → controller/route → tests). Vertical slices minimize merge conflicts and blocking dependencies.
- **Day 6–7 (Sprint 2 + Frontend):** Split 2-and-2 — **M1+M2 continue backend hardening**; **M3+M4 move to React frontend** (they own the create/detail/list endpoints already, so they have the most context) — **frontend now starts with the KPI Dashboard as the landing page**, per customer directive.
- **Cross-cutting rule:** everyone reviews everyone's PRs (small team — keep reviews quick, not a bottleneck). Daily 10-min stand-up (what I did / doing / blockers).

---

## 2. Module Ownership Matrix (Sprint 0–1, Day 3–5)

| Member | Module | Deliverables | Depends on |
|---|---|---|---|
| **M1** | **Foundation & Persistence + Accounts (new)** | Project skeleton (Maven, `application.yml`, Git repo + branch protection); `Payment` JPA entity + Flyway migration (MySQL schema per `04-SRS.md` §5); **`Account` JPA entity + migration + seed data (MEM-017)**; `PaymentRepository`/`AccountRepository` incl. `findByIdempotencyKey`, `findByStatus`, `findByStatus(ACTIVE)`; `IdempotencyService`; unit + `@DataJpaTest` tests | Nothing (first to start — everyone else builds on this) |
| **M2** | **State Machine & Audit Trail + Resilience (new)** | `PaymentStatus` enum, `PaymentState` interface + 5 concrete state classes, `StatusTransitionEngine`, `PaymentStatusHistory` entity + repository + history endpoint route; **Resilience4j Circuit Breaker + Retry wiring around simulated send/complete (MEM-021)**; unit tests for every transition (valid + illegal) | M1's `Payment` entity (can start with a stub/interface in parallel, wire in once M1's entity lands — Day 2 sync point) |
| **M3** | **Create Payment & Validation & Error Contract + Security** | `POST /payments` route + `CreatePaymentRequest`/`PaymentResponse` DTOs (now with `sourceAccountId`); Bean Validation annotations; `PaymentValidator` strategies (Amount/Currency/**Account, now checking against real `Account` entity — MEM-017**) + `ValidationChain`; `GlobalExceptionHandler` + `ApiError` DTO (Appendix B mapping, extended with `ACCOUNT_NOT_FOUND`/`RATE_LIMIT_EXCEEDED`); **secure response headers + CORS lock-down (MEM-022)**; web-layer + unit tests | M1 (entity/repo), M2 (transition engine to call after create) |
| **M4** | **Read Endpoints, Accounts read API, Analytics & API Contract** | `GET /payments/{id}`, `GET /payments` (list/filter/paginate), **`GET /accounts`, `GET /accounts/{id}` (MEM-017)**, **`GET /analytics/summary`, `GET /analytics/trend` + `AnalyticsService` (MEM-019)**; `PaymentMapper`/`AccountMapper` (MapStruct); springdoc-openapi/Swagger UI wiring (validate it matches `docs/openapi.yaml`); web-layer tests | M1 (repository), M3 (DTO shapes, coordinate together on Day 2) |

### Cross-Cutting (shared, not one person's module)
| Task | Owner (whoever has bandwidth first) | Notes |
|---|---|---|
| `RateLimitFilter` (Bucket4j, MEM-020) | M1/M2 | Sits in front of all controllers — one filter, not per-module |
| Load/performance test scripts (§ testing strategy 2.6) | Whoever finishes their module first | Run against a near-final build in Sprint 2 |

### Suggested Day-by-Day within Sprint 0–1

| Day | M1 | M2 | M3 | M4 |
|---|---|---|---|---|
| **Day 3** | Project skeleton, Git setup, `Payment` + `Account` entities + Flyway migrations (incl. seed accounts) | `PaymentStatus` enum + `PaymentState` interface skeleton (stub logic) | DTOs (`CreatePaymentRequest`/`PaymentResponse`, now with `sourceAccountId`) + Bean Validation annotations | Swagger/OpenAPI dependency setup + skeleton controllers (health check, `/accounts` stub) |
| **Day 4** | `PaymentRepository`/`AccountRepository` + `IdempotencyService` + tests | Concrete state classes + `StatusTransitionEngine` wiring + `PaymentStatusHistory` entity/repo | `ValidationChain` + validators (incl. new `AccountValidator` against real accounts) + `RateLimitFilter` skeleton | `GET /payments/{id}`, `GET /accounts/*` + `PaymentMapper`/`AccountMapper`; sync with M3 on shared DTO shapes |
| **Day 5** | Idempotency integration tests; support others as needed | History endpoint + full transition unit test suite + Circuit Breaker wiring | `GlobalExceptionHandler` (incl. new error codes); POST /payments end-to-end wired to M1+M2 | `GET /payments` list/filter/pagination; `AnalyticsService`/`GET /analytics/*`; Swagger UI live and validated against `openapi.yaml`; explicit `/validate /send /complete` routes (paired with M2) |
| **Day 5 (end) — Integration Checkpoint** | **All 4:** merge, run full test suite together, walk through Acceptance Criteria (`04-SRS.md` §10) as a group, fix integration gaps | | | |

---

## 3. Sprint 2 Split (Day 6–7)

### Backend track — M1 + M2
| Owner | Tasks |
|---|---|
| M1 | Validation rule hardening/edge cases; search-by-reference/source-account query; concurrency (`@Version`) integration test; DB index/connection-pool tuning for 40k req/min target (NFR-10) |
| M2 | Simulated failure injection (deterministic + random per Q6 in `12-CLARIFICATION-QUESTIONS.md`); retry-safety review; Circuit Breaker tuning; Swagger polish; stretch feature candidate (Appendix E) if time allows |

### Frontend track — M3 + M4
| Owner | Tasks |
|---|---|
| M3 | React app skeleton (Vite+TS), routing (default route → Dashboard); **Create Payment screen with source-account dropdown**; **Payment Details + status history timeline** screen; graceful `429` handling |
| M4 | **KPI Dashboard screen (landing page, new)**; **Payment List screen** (table, filter by status, search); **error/failed-payment detail view**; API client wiring against `docs/openapi.yaml` |

### Shared (Day 6–7)
- Integration pass: frontend ↔ real backend (not mocks).
- Load test pass (§ testing strategy 2.6) against a near-final build.
- Bug bash — everyone tests everyone else's flows.
- Presentation prep: slides, demo script, rehearsal (see `00-ROADMAP.md` Phase 8).

---

## 4. Git Workflow & Collaboration Rules

- **Branch naming:** `feature/<member>-<short-desc>`, e.g. `feature/m2-status-transition-engine`, `feature/m1-account-entity`.
- **PRs required** for all merges to `main` — no direct pushes (per `03-SKILLS.md`).
- **PR description must reference** the module/day from this doc, e.g. "Implements M1 Day 3 — Account entity + seed data (MEM-017)".
- **Daily sync point:** end of Day 3, 4, and 5 — quick 10-min call to unblock cross-module dependencies (e.g. M2 needs M1's final `Payment`/`Account` entity shape; M3/M4 need to agree DTO field names match `docs/openapi.yaml` exactly).
- **Definition of Done per module (see also `07-TESTING-STRATEGY.md` §7):** code + unit tests + (if applicable) web-layer test + green build + PR reviewed by at least 1 other member.

## 5. Risk/Dependency Notes

- **M2 and M3 have the tightest coupling** (state engine must be callable from the create-payment flow) — recommend they pair/sync mid-Day-4 rather than waiting until Day 5 integration.
- **M4's Swagger validation step is important** — it's our early-warning check that the real API matches `docs/openapi.yaml`, which the frontend (M3/M4 themselves, later) will depend on. Any drift found should be fixed immediately, not deferred.
- **New (MEM-017):** M1's `Account` seed data (Flyway) is a hard dependency for M3's `AccountValidator` tests and M4's `GET /accounts` — prioritize it Day 3 morning.
- **New (MEM-020):** `RateLimitFilter` must be wired in *before* any load testing is meaningful — treat it as a Day 4 priority, not a Day 6 afterthought.
- If any member finishes early, next-best use of time (in priority order): (1) help unblock the M2/M3 coupling, (2) add missing tests per `07-TESTING-STRATEGY.md` (incl. new §2.5–2.8), (3) start Sprint 2 backlog item early.

---

## 6. Fill In Real Names

| Placeholder | Real Name |
|---|---|
| M1 | _TBD_ |
| M2 | _TBD_ |
| M3 | _TBD_ |
| M4 | _TBD_ |

*(Tell me the names and I'll do a find-and-replace across all docs in one pass.)*

