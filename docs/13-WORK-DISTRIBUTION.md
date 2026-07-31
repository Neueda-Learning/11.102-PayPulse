# 👥 WORK-DISTRIBUTION.md — Final Work Allocation (Team of 4, Full-Stack Parallel)

Related: `00-ROADMAP.md` (phases/dates), `01-CONTEXT.md`/`04-SRS.md` (requirements), `05-ARCHITECTURE.md` (package structure — read before starting), `06-DESIGN-PATTERNS.md`, `07-TESTING-STRATEGY.md` (test ownership), `11-API-DESIGN.md` + `openapi.yaml` (frozen API contract), `../chirag/04-wireframes/` (clickable HTML reference for every screen).

> **Finalized 31 Jul 2026.** This supersedes the earlier "everyone-on-backend-first, split-to-frontend-later" plan. Every member now owns **one feature vertical, full-stack (backend + frontend + tests), in parallel, starting Day 1** — this is what lets all 4 people start working at the same time without waiting on each other. The API contract (`openapi.yaml`) is **frozen first** (see §0) so nobody blocks on anybody else's in-progress work.

---

## 0. Precondition — API Contract is Frozen (done)

Before any code is written, `docs/openapi.yaml` + `docs/11-API-DESIGN.md` are the **single source of truth** and have been re-verified for consistency (31 Jul 2026 pass: every endpoint now documents its `429` rate-limit response consistently; all example IDs use realistic UUID format). **No member changes the API shape unilaterally** — a contract change is a 5-minute group discussion, then a doc update, then code follows. This is what makes true parallel work safe.

---

## 1. Strategy

- **Every member owns one feature vertical end-to-end:** entity/schema → repository → service/business logic → controller/route → tests → the matching React screen(s)/component(s) → frontend tests. No "backend people" and "frontend people" — everyone does both, for their own slice.
- **Shared components have a defined contract (props/interface) agreed up front (§3)** so two members can build independently and integrate on the sync day without surprises.
- **The `../chirag/04-wireframes/*.html` prototype is the visual/UX reference for every screen** — it already shows the exact fields, states, and flows to build against; treat it as "the design is done, now make it real with the actual API."
- **Daily 15-min stand-up:** what I did / doing / blockers, plus explicit call-out of anyone touching a shared file (see §4) that day.
- Everyone reviews everyone's PRs (small team — quick look, not a bottleneck).

---

## 2. Feature Ownership Matrix (4 members × 3 feature-units each — evenly split)

Each row = one person's complete, independent vertical slice. Feature numbers refer to `../chirag/01-feature-list.md`.

### 🟦 M1 — Accounts, Idempotency & Currency Foundation

| | |
|---|---|
| **Features owned** | #2 Multi-Account Support · #9 Duplicate Payment Protection (Idempotency) · #12 INR/USD Currency Support (schema-level) |
| **Backend files** | `payment/domain/Payment.java` (entity, incl. `sourceAccountId`, `currency`, `idempotencyKey`, `version`) · `account/domain/Account.java` + `AccountStatus` enum · `account/repository/AccountRepository.java` · `payment/repository/PaymentRepository.java` (incl. `findByIdempotencyKey`, `findBySourceAccountId`) · `common/idempotency/IdempotencyService.java` · `src/main/resources/db/migration/V1__create_payment_table.sql`, `V2__create_account_table.sql`, `V3__seed_accounts.sql` (3 seed accounts: 1 ACTIVE-INR, 1 ACTIVE-USD, 1 INACTIVE-INR — matches `../chirag/04-wireframes/assets/dummy-data.js`) |
| **Frontend files** | `frontend/src/features/accounts/AccountsPage.tsx` (list view — port of `accounts.html`) · `frontend/src/components/AccountPicker.tsx` — **shared component**, contract in §3, consumed by M3 |
| **Tests** | `payment/repository/PaymentRepositoryTest` (`@DataJpaTest`), `account/repository/AccountRepositoryTest`, `common/idempotency/IdempotencyServiceTest`, Flyway migration smoke test |
| **Depends on** | Nothing — starts first, everyone else's entities/repos build on this Day 1 |
| **Blocks** | M2 (needs `Payment`/`PaymentStatus` shape), M3 (needs `Account` entity + `AccountPicker` contract), M4 (needs `AccountRepository` for `GET /accounts`) |

### 🟩 M2 — State Machine, Audit Trail, Resilience & Rate Limiting

| | |
|---|---|
| **Features owned** | #4 Automatic Payment Lifecycle Processing · #6 Payment Status History / Audit Trail · #11 API Rate Limiting (+ resilience bundled in) |
| **Backend files** | `payment/domain/PaymentStatus.java` (enum) · `payment/domain/PaymentStatusHistory.java` · `payment/service/states/{CreatedState,ValidatedState,SentState,CompletedState,FailedState}.java` · `payment/service/StatusTransitionEngine.java` · `payment/repository/PaymentStatusHistoryRepository.java` · `payment/api/PaymentController.java` — **owns methods:** `getHistory()`, `validatePayment()`, `sendPayment()`, `completePayment()` (see §4 for shared-file protocol) · `common/ratelimit/RateLimitFilter.java` (Bucket4j) · `common/resilience/ResilienceConfig.java` (Resilience4j Circuit Breaker + Retry around simulated send/complete) |
| **Frontend files** | `frontend/src/components/StatusHistoryTimeline.tsx` — **shared component**, contract in §3, consumed by M4 · `frontend/src/components/RateLimitToast.tsx` — global "please slow down" banner (app-wide, mounted in `App.tsx`), reacts to any `429` response |
| **Tests** | Full state-transition unit test suite (valid + illegal, all 5 states) · `PaymentStatusHistoryRepositoryTest` · `RateLimitFilterTest` (boundary: N+1th request rejected) · Circuit breaker open/close test · load-test script (`load-tests/`, §7-testing-strategy 2.6) |
| **Depends on** | M1's `Payment` entity (can stub `PaymentStatus`/interface Day 1, wire in once M1's entity lands — Day 1 end-of-day sync) |
| **Blocks** | M3 (needs `StatusTransitionEngine.validate()` callable from `PaymentService.createPayment()`) |

### 🟨 M3 — Create Payment, Validation & Security

| | |
|---|---|
| **Features owned** | #3 Create Payment (with Account Selection) · #10 Consistent Validation & Error Messaging (+ Security Hardening cross-cutting) |
| **Backend files** | `payment/api/PaymentController.java` — **owns method:** `create()` only (see §4 shared-file protocol) · `payment/api/dto/{CreatePaymentRequest,PaymentResponse}.java` · `payment/api/PaymentMapper.java` (MapStruct, shared shape with M4) · `payment/service/PaymentService.java` (orchestration: idempotency check → account check → save → trigger validate/send/complete) · `payment/service/validators/{AmountValidator,CurrencyValidator,AccountValidator}.java` + `payment/service/ValidationChain.java` · `common/error/{ErrorCode,ApiError,GlobalExceptionHandler}.java` (all error codes incl. `ACCOUNT_NOT_FOUND`, `RATE_LIMIT_EXCEEDED`) · `common/config/{CorsConfig,SecurityHeadersConfig}.java` |
| **Frontend files** | `frontend/src/features/payments/CreatePaymentPage.tsx` — full port of `create-payment.html` (uses M1's `AccountPicker`, real `POST /payments` call, real idempotency-key generation, inline validation errors) |
| **Tests** | Unit tests: `AmountValidator`, `CurrencyValidator`, `AccountValidator`, `ValidationChain` · Web-layer test: `POST /payments` (success + every 400/404/429 case) · `GlobalExceptionHandler` test (asserts no stack traces ever leak) · Security tests: injection probes, oversized payload, header assertions (per `07-TESTING-STRATEGY.md` §2.8) |
| **Depends on** | M1 (`Payment`/`Account` entities, `AccountPicker` contract), M2 (`StatusTransitionEngine.validate()`) |
| **Blocks** | Nothing downstream — last in the create-payment chain |

### 🟧 M4 — Read/List/Search, KPI Dashboard & Analytics

| | |
|---|---|
| **Features owned** | #1 KPI Dashboard (Landing Page) · #5 Payment Status & Details View · #7+#8 Payment List with Status Filter + Search (one screen/endpoint, counted as one unit) |
| **Backend files** | `payment/api/PaymentController.java` — **owns methods:** `getById()`, `list()` (see §4 shared-file protocol) · `account/api/AccountController.java` (`GET /accounts`, `GET /accounts/{id}`) · `analytics/api/AnalyticsController.java` (`GET /analytics/summary`, `GET /analytics/trend`) · `analytics/service/AnalyticsService.java` (aggregation queries) · `analytics/dto/{KpiSummaryResponse,TrendResponse}.java` · `account/api/AccountMapper.java` · springdoc-openapi wiring + validate against `docs/openapi.yaml` |
| **Frontend files** | `frontend/src/features/dashboard/KpiDashboardPage.tsx` — port of `index.html` (default route `/`) · `frontend/src/features/payments/PaymentListPage.tsx` — port of `payments.html` (filter + search) · `frontend/src/features/payments/PaymentDetailsPage.tsx` — port of `payment-details.html` (**uses M2's `StatusHistoryTimeline` component**) · `frontend/src/components/KpiCard.tsx`, `StatusBadge.tsx` (shared, but M4 owns/creates first since Dashboard needs them first) |
| **Tests** | Web-layer tests: `GET /payments/{id}`, `GET /payments`, `GET /accounts*`, `GET /analytics/*` (success + 404/429 cases) · `AnalyticsServiceTest` (correct success/failure rate math, zero-payments edge case) · `PaymentMapper`/`AccountMapper` null-handling tests |
| **Depends on** | M1 (`AccountRepository`), M3 (`PaymentMapper` shape, coordinate Day 1), M2 (`StatusHistoryTimeline` contract) |
| **Blocks** | Nothing downstream — read-only endpoints |

**Balance check:** 3 feature-units per person (12 Must-Have features ÷ 4). Backend files ≈ even (5–7 files each). Frontend: each person owns 1–2 full pages + at most 1 shared component. No one is "just backend" or "just frontend."

---

## 3. Shared Component Contracts (agree on these Day 1 morning, before writing code)

To let people build in parallel without waiting on each other, freeze these tiny interfaces first:

| Component | Owner (builds it) | Consumer(s) | Contract |
|---|---|---|---|
| `AccountPicker` | M1 | M3 (Create Payment form) | Props: `{ accounts: AccountResponse[], value: string \| null, onChange: (accountId: string) => void }`. Renders a `<select>`; disables/greys out `INACTIVE` accounts. Does **not** fetch data itself — parent passes `accounts` in (keeps it a dumb/presentational component, easy to stub with `../chirag/04-wireframes/assets/dummy-data.js`-shaped fixtures before the real API exists). |
| `StatusHistoryTimeline` | M2 | M4 (Payment Details page) | Props: `{ history: PaymentHistoryResponse[] }`. Renders the ordered timeline exactly as in `../chirag/04-wireframes/payment-details.html`. Pure/presentational — no fetching. |
| `PaymentMapper` (backend, MapStruct) | M3 (creates it for `create()`/`PaymentResponse`) | M4 (reuses for `getById()`/`list()`) | Interface signature frozen from `08-UML-CLASS-DIAGRAM.md`: `toEntity(CreatePaymentRequest)`, `toResponse(Payment)`, `toHistoryResponse(PaymentStatusHistory)`. M3 creates the file Day 1; M4 only *adds* usages, never changes the mapping signatures without a heads-up. |
| `ApiError` / error contract | M3 (owns `GlobalExceptionHandler`) | Everyone (frontend renders `errorCode`/`message` the same way everywhere) | Shape frozen in `openapi.yaml` `components.schemas.ApiError` — do not add fields without updating the spec first. |
| `RateLimitToast` | M2 | Mounted once in `App.tsx` (whoever sets up the app skeleton, likely M4 since they own the default route) — reacts to any `429` globally via a shared Axios/fetch interceptor | Props: none (reads from a small global event bus/context that any API call publishes to on `429`). |

---

## 4. Shared-File Protocol — `PaymentController.java`

Per `08-UML-CLASS-DIAGRAM.md`, `PaymentController` is a single class with 7 methods. Rather than splitting it into 7 controller classes (which would diverge from the already-agreed class diagram), **three people add different methods to the same file**:

- **M3** adds `create()` — Day 1–2 (first, since nothing else depends on it existing yet).
- **M4** adds `getById()`, `list()` — Day 1–2, in parallel (different methods, low conflict risk).
- **M2** adds `getHistory()`, `validatePayment()`, `sendPayment()`, `completePayment()` — Day 2, after `StatusTransitionEngine` exists.

**Rule to avoid merge pain:** each person's PR touches *only their own method(s)* plus the class-level constructor injection line for their own dependency (e.g. M2's PR adds `StatusTransitionEngine` to the constructor). Pull `main` and rebase before opening a PR; whoever merges second resolves the (usually trivial, additive) conflict. If this ever gets messy in practice, escalate in stand-up — splitting into separate controllers is an acceptable fallback and only requires a one-line update to `05-ARCHITECTURE.md`/the class diagram.

---

## 5. Day-by-Day Plan (all 4 in parallel from Day 1)

| Day | M1 | M2 | M3 | M4 |
|---|---|---|---|---|
| **Day 1 (31 Jul, today)** | `Payment`+`Account` entities, Flyway migrations + seed data, `AccountPicker` component skeleton | `PaymentStatus` enum, `PaymentState` interface + stub states, `RateLimitFilter` skeleton | DTOs (`CreatePaymentRequest`/`PaymentResponse`), Bean Validation annotations, `GlobalExceptionHandler` skeleton | Project skeleton (Vite+TS), router (`/` → Dashboard), springdoc-openapi setup, `KpiCard`/`StatusBadge` components |
| **Day 2** | `PaymentRepository`/`AccountRepository` + `IdempotencyService` + tests; hand off `Account` entity shape to M3/M4 | Concrete state classes + `StatusTransitionEngine`; `PaymentStatusHistory` entity/repo | Validators + `ValidationChain`; wire `PaymentService.createPayment()` (mocks M2's engine until it lands) | `AccountController` (`GET /accounts*`), `AnalyticsService` skeleton |
| **Day 3** | `AccountsPage.tsx` (frontend); support others | `StatusTransitionEngine` fully wired to real `PaymentService`; `RateLimitFilter` live; Circuit Breaker wiring | `PaymentController.create()` end-to-end; `CreatePaymentPage.tsx` (frontend, real API call) | `PaymentController.getById()/list()`; `AnalyticsController`; `KpiDashboardPage.tsx`, `PaymentListPage.tsx` (frontend) |
| **Day 4** | Idempotency integration tests; currency/account edge-case tests | `PaymentController.getHistory()/validate()/send()/complete()`; `StatusHistoryTimeline.tsx`; load-test script draft | Security tests (§2.8); `GlobalExceptionHandler` full coverage; polish `CreatePaymentPage` (429 handling via `RateLimitToast`) | `PaymentDetailsPage.tsx` (integrates M2's timeline); Swagger UI validated against `openapi.yaml` |
| **Day 4 (end) — Integration Checkpoint** | **All 4:** merge everything to `main`, run full backend + frontend test suites together, click through the real app end-to-end against the real API (not the `chirag` dummy data anymore), walk through `04-SRS.md` §10 Acceptance Criteria as a group | | | |
| **Day 5** | Search-by-account query; concurrency (`@Version`) test; support wherever needed | Failure-injection (deterministic test accounts, per `12-CLARIFICATION-QUESTIONS.md` Q6); resilience tuning; run load test (~40k req/min target) | Bug bash on Create Payment flow; CORS/security header final pass | Bug bash on Dashboard/List/Details; CSV export stretch (#14) if time allows |
| **Day 6–7** | Stretch items (Appendix E) if ahead of schedule; final polish | Stretch items; final polish | Stretch items; final polish | Presentation prep: demo script, slides (Phase 8) |

---

## 6. Git Workflow & Collaboration Rules

- **Branch naming:** `feature/<member>-<short-desc>`, e.g. `feature/m1-account-entity`, `feature/m3-create-payment-endpoint`.
- **PRs required** for all merges to `main` — no direct pushes.
- **PR description must reference** the feature number from `../chirag/01-feature-list.md`, e.g. "Implements Feature #3 — Create Payment (M3, Day 3)".
- **Daily sync point:** every stand-up — call out if you're touching `PaymentController.java` or any of the §3 shared components that day.
- **Contract changes are never silent:** if you need to change a shared component's props or the API shape, say so in stand-up *before* changing it, then update `openapi.yaml`/`11-API-DESIGN.md`/§3 of this doc in the same PR.
- **Definition of Done per feature (see also `07-TESTING-STRATEGY.md` §7):** backend code + frontend code + unit tests + web-layer test (backend) + green build + PR reviewed by at least 1 other member + manually clicked through in the browser.

## 7. Risk / Dependency Notes

- **Tightest coupling:** M3 needs M2's `StatusTransitionEngine.validate()` to exist before `PaymentService.createPayment()` can be finished end-to-end — M3 mocks it Day 2, swaps to the real thing Day 3. Pair/sync mid-Day-2 rather than waiting for Day 4 integration.
- **M1's seed accounts are a hard dependency** for M3's `AccountValidator` tests, M4's `GET /accounts`, and both frontend `AccountPicker`/`AccountsPage` — prioritize Day 1 afternoon, don't let it slip to Day 2.
- **`RateLimitFilter` (M2) must be live before Day 5's load test is meaningful** — Day 2–3 priority, not a Day 6 afterthought.
- **Frontend `AccountPicker` and `StatusHistoryTimeline` contracts (§3) are frozen Day 1** — if M1/M2 change the props after M3/M4 have started integrating, that's a same-day heads-up in stand-up, not a silent breaking change.
- **`../chirag/04-wireframes/*.html` is the UX reference, not the deliverable** — don't spend time perfecting the dummy HTML further; port the layout/behavior into the real React app.
- If any member finishes their 3 feature-units early, next-best use of time (priority order): (1) help unblock whoever's on the critical path (M2→M3 coupling above), (2) add missing tests per `07-TESTING-STRATEGY.md` §2.5–2.8, (3) pick up a Good-to-Have feature (#13–#17 in `../chirag/01-feature-list.md`).

---

## 8. Fill In Real Names

| Placeholder | Real Name | Vertical |
|---|---|---|
| M1 | _TBD_ | Accounts, Idempotency & Currency Foundation |
| M2 | _TBD_ | State Machine, Audit Trail, Resilience & Rate Limiting |
| M3 | _TBD_ | Create Payment, Validation & Security |
| M4 | _TBD_ | Read/List/Search, KPI Dashboard & Analytics |

*(Tell me the names and I'll do a find-and-replace across all docs in one pass.)*



