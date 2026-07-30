# 👥 WORK-DISTRIBUTION.md — Team Task Allocation (Team of 4)

Related: `00-ROADMAP.md` (phases/dates), `03-SKILLS.md` §3 (original split summary), `05-ARCHITECTURE.md` (package structure), `07-TESTING-STRATEGY.md` (test ownership).

> This is the **single, authoritative work-breakdown** — treat it like a lightweight Trello board in Markdown. Replace **M1–M4** with real names as soon as assigned. Update checkboxes as work completes (or mirror this into Trello/GitHub Projects per `03-SKILLS.md`).

---

## 1. Strategy Recap

- **Day 1 (today):** Done as a team — design docs (Phases 0–4). ✅
- **Day 2–4 (Sprint 0 + Sprint 1 — CORE):** All 4 members on **backend**, each owning one **vertical module** end-to-end (entity/schema → service/logic → controller/route → tests). Vertical slices minimize merge conflicts and blocking dependencies.
- **Day 5–7 (Sprint 2 + Frontend):** Split 2-and-2 — **M1+M2 continue backend hardening**; **M3+M4 move to React frontend** (they own the create/detail/list endpoints already, so they have the most context).
- **Cross-cutting rule:** everyone reviews everyone's PRs (small team — keep reviews quick, not a bottleneck). Daily 10-min stand-up (what I did / doing / blockers).

---

## 2. Module Ownership Matrix (Sprint 0–1, Day 2–4)

| Member | Module | Deliverables | Depends on |
|---|---|---|---|
| **M1** | **Foundation & Persistence** | Project skeleton (Maven, `application.yml`, Git repo + branch protection); `Payment` JPA entity + Flyway migration (MySQL schema per `04-SRS.md` §5); `PaymentRepository` incl. `findByIdempotencyKey`, `findByStatus`; `IdempotencyService`; unit + `@DataJpaTest` tests | Nothing (first to start — everyone else builds on this) |
| **M2** | **State Machine & Audit Trail** | `PaymentStatus` enum, `PaymentState` interface + 5 concrete state classes, `StatusTransitionEngine`, `PaymentStatusHistory` entity + repository + history endpoint route; unit tests for every transition (valid + illegal) | M1's `Payment` entity (can start with a stub/interface in parallel, wire in once M1's entity lands — Day 2 sync point) |
| **M3** | **Create Payment & Validation & Error Contract** | `POST /payments` route + `CreatePaymentRequest`/`PaymentResponse` DTOs; Bean Validation annotations; `PaymentValidator` strategies (Amount/Currency/Account) + `ValidationChain`; `GlobalExceptionHandler` + `ApiError` DTO (Appendix B mapping); web-layer + unit tests | M1 (entity/repo), M2 (transition engine to call after create) |
| **M4** | **Read Endpoints & API Contract** | `GET /payments/{id}`, `GET /payments` (list/filter/paginate), `PaymentMapper` (MapStruct); springdoc-openapi/Swagger UI wiring (validate it matches `docs/openapi.yaml`); web-layer tests | M1 (repository), M3 (DTO shapes, coordinate together on Day 2) |

### Suggested Day-by-Day within Sprint 0–1

| Day | M1 | M2 | M3 | M4 |
|---|---|---|---|---|
| **Day 2** | Project skeleton, Git setup, `Payment` entity + Flyway migration | `PaymentStatus` enum + `PaymentState` interface skeleton (stub logic) | DTOs (`CreatePaymentRequest`/`PaymentResponse`) + Bean Validation annotations | Swagger/OpenAPI dependency setup + skeleton controller (health check route) |
| **Day 3** | `PaymentRepository` + `IdempotencyService` + tests | Concrete state classes + `StatusTransitionEngine` wiring + `PaymentStatusHistory` entity/repo | `ValidationChain` + validators; wire into `PaymentService.createPayment()` | `GET /payments/{id}` + `PaymentMapper`; sync with M3 on shared DTO shapes |
| **Day 4** | Idempotency integration tests; support others as needed | History endpoint + full transition unit test suite | `GlobalExceptionHandler`; POST /payments end-to-end wired to M1+M2 | `GET /payments` list/filter/pagination; Swagger UI live and validated against `openapi.yaml`; explicit `/validate /send /complete` routes (paired with M2) |
| **Day 4 (end) — Integration Checkpoint** | **All 4:** merge, run full test suite together, walk through Acceptance Criteria (`04-SRS.md` §9) as a group, fix integration gaps | | | |

---

## 3. Sprint 2 Split (Day 5–7)

### Backend track — M1 + M2
| Owner | Tasks |
|---|---|
| M1 | Validation rule hardening/edge cases; search-by-reference query; concurrency (`@Version`) integration test |
| M2 | Simulated failure injection (deterministic + random per Q6 in `12-CLARIFICATION-QUESTIONS.md`); retry-safety review; Swagger polish; stretch feature candidate (Appendix E) if time allows |

### Frontend track — M3 + M4
| Owner | Tasks |
|---|---|
| M3 | React app skeleton (Vite+TS), routing; **Create Payment screen**; **Payment Details + status history timeline** screen |
| M4 | **Payment List screen** (table, filter by status, search); **error/failed-payment detail view**; API client wiring against `docs/openapi.yaml` |

### Shared (Day 6–7)
- Integration pass: frontend ↔ real backend (not mocks).
- Bug bash — everyone tests everyone else's flows.
- Presentation prep: slides, demo script, rehearsal (see `00-ROADMAP.md` Phase 8).

---

## 4. Git Workflow & Collaboration Rules

- **Branch naming:** `feature/<member>-<short-desc>`, e.g. `feature/m2-status-transition-engine`.
- **PRs required** for all merges to `main` — no direct pushes (per `03-SKILLS.md`).
- **PR description must reference** the module/day from this doc, e.g. "Implements M2 Day 3 — StatusTransitionEngine".
- **Daily sync point:** end of Day 2, 3, and 4 — quick 10-min call to unblock cross-module dependencies (e.g. M2 needs M1's final `Payment` entity shape; M3/M4 need to agree DTO field names match `docs/openapi.yaml` exactly).
- **Definition of Done per module (see also `07-TESTING-STRATEGY.md` §7):** code + unit tests + (if applicable) web-layer test + green build + PR reviewed by at least 1 other member.

## 5. Risk/Dependency Notes

- **M2 and M3 have the tightest coupling** (state engine must be callable from the create-payment flow) — recommend they pair/sync mid-Day-3 rather than waiting until Day 4 integration.
- **M4's Swagger validation step is important** — it's our early-warning check that the real API matches `docs/openapi.yaml`, which the frontend (M3/M4 themselves, later) will depend on. Any drift found should be fixed immediately, not deferred.
- If any member finishes early, next-best use of time (in priority order): (1) help unblock the M2/M3 coupling, (2) add missing tests per `07-TESTING-STRATEGY.md`, (3) start Sprint 2 backlog item early.

---

## 6. Fill In Real Names

| Placeholder | Real Name |
|---|---|
| M1 | _TBD_ |
| M2 | _TBD_ |
| M3 | _TBD_ |
| M4 | _TBD_ |

*(Tell me the names and I'll do a find-and-replace across all docs in one pass.)*

