# 👥 WORK-DISTRIBUTION-V2.md — V2 Enhancement Wave Work Allocation (Team of 4)

> **Status:** 🚧 In progress, kicked off **05 Aug 2026**, targeting the **06 Aug 2026** presentation deadline.
> **Precondition:** Core (MVP, Features #1–#12) is complete and shipped — see `docs/13-WORK-DISTRIBUTION.md` (kept as the historical Core record, not edited).
> Related: `00-ROADMAP.md` §Phase 9, `01-CONTEXT.md` §4b, `02-MEMORY.md` MEM-023–034, `04-SRS.md` §12–15, `05-ARCHITECTURE.md` §8, `06-DESIGN-PATTERNS.md` (V2 additions), `07-TESTING-STRATEGY.md` §8–9, `08-UML-CLASS-DIAGRAM.md` §3, `09-UML-SEQUENCE-DIAGRAMS.md` (V2 diagrams 10–14), `10-UML-STATE-DIAGRAM.md` §6, `11-API-DESIGN.md` §14–21, `openapi.yaml` (v2.0.0), `chirag/01-feature-list.md` (feature numbers referenced throughout).

---

## 0. Precondition — API Contract Frozen (Again, for V2)

Exactly as in Core (`13-WORK-DISTRIBUTION.md` §0): before any V2 code is written, `docs/openapi.yaml` (now `v2.0.0`) + `docs/11-API-DESIGN.md` §14–21 are the **single source of truth** for every new endpoint (`/payments/{id}/cancel`, `/payments/{id}/reverse`, `/payments/export`, `/analytics/stream`, `/fx/rate`, and the newly-documented `/notifications/*` routes). **No member changes the API shape unilaterally** — same discipline as Core, just faster-cycle given the shorter runway to the deadline.

---

## 1. Strategy — Continuity Over Reshuffling

Per `02-MEMORY.md` MEM-024, **every member continues owning the module they built in Core** rather than being reassigned by availability — this preserves accumulated context under a very short V2 window (kickoff same day as the original deadline target):

- **M1** kept cross-cutting/infrastructure ownership (Core: Accounts, Idempotency, Rate Limiting) → V2: Notifications wiring, Sortable Columns, Load Test v2, Dashboard SSE.
- **M2** kept the payment lifecycle/state-machine ownership (Core: State Machine, Audit Trail, Resilience) → V2: Create-Payment frontend audit, Cancellation, Reversal (both are state-machine-adjacent).
- **M3** kept validation/create-payment ownership in Core but the V2 assignment shifts to the **read/reporting side** (deepening analytics, adding export) to balance load with M2 taking on two state-machine features.
- **M4** kept read/list/KPI ownership (Core: Read/List/Search, KPI Dashboard, Analytics) → V2: Copy/Deep-Linking (a natural extension of the list/details screens they already own), plus full feature #20 cross-currency conversion using a hardcoded present USD rate instead of a live lookup.

Everyone reviews everyone's PRs — same lightweight process as Core (`13-WORK-DISTRIBUTION.md` §1).

---

## 2. Feature Ownership Matrix (V2)

Feature numbers refer to `../chirag/01-feature-list.md`.

### 🟦 M1 — Notifications Wiring, Sortable Columns, Load Test v2, Dashboard SSE

| | |
|---|---|
| **Features owned** | #21 Notifications (wire existing module) · #16 Sortable Columns · Load Test v2 (infra) · Dashboard SSE (replaces 30s poll, not a numbered feature but explicitly requested) |
| **Backend files** | `payment/event/PaymentNotificationListener.java` (new — `@TransactionalEventListener(AFTER_COMMIT)` + `@Async`, subscribes to existing `PaymentStatusChangedEvent`) · `account/domain/Account.java` (add `ownerEmail`/`ownerName` fields) · `src/main/resources/db/migration/V6__add_account_owner_contact.sql` (new) · `common/config/AsyncConfig.java` (new — `notification-executor` `ThreadPoolTaskExecutor` bean) · `payment/api/PaymentController.java` — sort-field allow-list validation on `list()` · `analytics/service/DashboardStreamService.java` (new — `SseEmitter` registry, debounced push) · `analytics/api/AnalyticsController.java` — adds `stream()` method (shared-file protocol, same as Core §4) |
| **Frontend files** | `frontend/payments.html` — clickable sortable column headers (Amount/Status/Created) with ▲/▼ indicator · `frontend/index.html` — replace `setInterval(loadDashboard, 30000)` with `EventSource('/api/v1/analytics/stream')`, graceful fallback to the existing poll on connection failure · `frontend/nginx/nginx.conf` — `proxy_buffering off` on the `/api/v1/analytics/stream` location block |
| **Tests** | `PaymentNotificationListenerTest` (unit, mocked `NotificationService`/`AccountService`) · async-isolation integration test (slow/failing notification doesn't delay `POST /payments`) · `DashboardStreamServiceTest` (debounce/coalesce behavior) · web-layer test for `sort` allow-list rejection · extended `load-tests/` script covering `/cancel`, `/reverse`, `/export` |
| **Depends on** | Existing `PaymentStatusChangedEvent` (already published by `StatusTransitionEngine` since Core) — no changes needed there |
| **Blocks** | Nothing downstream — all 4 sub-items are independently shippable |
| **First task, in order** | 1) `V6` migration + `Account` field additions (unblocks nothing else, but needed before notification wiring can resolve an email). 2) `PaymentNotificationListener`. 3) Sortable columns (independent, quick win). 4) Dashboard SSE (largest sub-item, start early). 5) Load test extension (last — needs the cancel/reverse endpoints from M2 to exist first). |

### 🟩 M2 — Create-Payment Frontend Audit, Payment Cancellation, Payment Reversal

| | |
|---|---|
| **Features owned** | Create Payment frontend cross-check (audit task, no feature number — quality gate) · #18 Payment Cancellation · #19 Payment Reversal |
| **Backend files** | `payment/service/states/CancelledState.java` (new — implements existing `PaymentState` interface) · `payment/service/StatusTransitionEngine.java` — register `CancelledState` in the states map (one-line addition per `08-UML-CLASS-DIAGRAM.md` §3 note) · `payment/service/PaymentService.java` — add `cancelPayment(id)` orchestration · `payment/service/ReversalService.java` (new — `reverse(id)`, reuses `PaymentService.createPayment()`) · `payment/domain/Payment.java` — add `reversed`, `reversalPaymentId`, `reversalOfPaymentId` fields · `src/main/resources/db/migration/V7__add_payment_cancellation_reversal.sql` (new — adds `payment.reversed`/`reversal_payment_id`/`reversal_of_payment_id`, and extends the `status` check-constraint/enum to include `CANCELLED`) · `common/error/ErrorCode.java`/`GlobalExceptionHandler.java` — add `PAYMENT_NOT_CANCELLABLE` (409), `PAYMENT_ALREADY_REVERSED` (409) · `payment/api/PaymentController.java` — **owns new methods:** `cancel()`, `reverse()` (shared-file protocol, same discipline as Core §4) |
| **Frontend files** | `frontend/js/api.js` — **fix `API_BASE` hardcoding** (MEM-028, first task below) · `frontend/js/api.js` — add `Payments.cancel(id)`, `Payments.reverse(id)` · `frontend/payment-details.html` — "Cancel" button (visible only when `status === 'CREATED'`), "Reverse" button (visible only when `status === 'COMPLETED' && !reversed`), display `reversalPaymentId`/`reversalOfPaymentId` as a linked-payment reference when present |
| **Tests** | `CancelledStateTest` (unit) · `ReversalServiceTest` (unit, mocked repo/service) · web-layer tests: `POST /payments/{id}/cancel` (200/409/404), `POST /payments/{id}/reverse` (201/409×2/404) · integration test: full create→cancel round trip; full create→complete→reverse round trip asserting original untouched + new payment independently processed |
| **Depends on** | Nothing new structurally — reuses M2's own Core `StatusTransitionEngine`/`PaymentService` |
| **Blocks** | M1's load-test v2 extension (needs `/cancel`/`/reverse` to exist first) |
| **First task, in order (per MEM-028)** | 1) **Fix `API_BASE` in `frontend/js/api.js`** and verify via `docker-compose up` that nginx proxies correctly — do this **before** anything else, it's a pre-existing bug, not new V2 work. 2) `V7` migration + entity fields. 3) `CancelledState` + cancel endpoint. 4) `ReversalService` + reverse endpoint. 5) Frontend buttons. |

### 🟨 M3 — Analytics/Trend View (Deepened), CSV Export

| | |
|---|---|
| **Features owned** | #13 Basic Analytics/Trend View (deepen beyond the existing MVP bar chart) · #14 Export Payment List (CSV) |
| **Backend files** | `analytics/service/AnalyticsService.java` — extend `computeTrend()` with a configurable window param + per-currency breakdown (currently hardcoded 24h/status-only) · `analytics/dto/TrendResponse.java` — extend `TrendBucket` with per-currency volume fields · `common/export/PaymentCsvExportService.java` (new — streaming `Stream<Payment>` → CSV writer) · `payment/repository/PaymentRepository.java` — add a `Stream<Payment>` cursor-based query method for export (alongside the existing `Page<Payment>` methods, not replacing them) · `payment/api/PaymentController.java` — **owns new method:** `export()` (shared-file protocol) · `common/error/ErrorCode.java` — add `EXPORT_TOO_LARGE` (400) · `application.yml` — `paypulse.export.max-rows: 50000` |
| **Frontend files** | `frontend/index.html` — deepen the trend chart (configurable window selector e.g. 24h/7d, per-currency toggle) · `frontend/payments.html` — "Export CSV" button triggering `GET /payments/export` with current filters, using `<a download>`/`Blob` to trigger the browser download |
| **Tests** | `AnalyticsServiceTest` — new window/per-currency aggregation cases (incl. zero-payments edge case, reused from Core) · `PaymentCsvExportServiceTest` — CSV header/escaping correctness · web-layer test: `GET /payments/export` (200 + `text/csv`, 400 `EXPORT_TOO_LARGE` when over cap) |
| **Depends on** | Nothing new — extends existing `AnalyticsService`/`PaymentRepository` from Core |
| **Blocks** | Nothing downstream |

### 🟧 M4 — Copy Payment ID / Deep Linking, Multi-Currency Conversion (Hardcoded Current USD Rate)

| | |
|---|---|
| **Features owned** | #17 Copy Payment ID / Deep Linking · #20 Multi-Currency Conversion (real cross-currency payout support using a hardcoded present USD rate instead of any live/looked-up provider) |
| **Backend files** | `fx/api/FxController.java` (new — `GET /fx/rate`) · `fx/service/FxRateService.java` (interface) + `StaticConfigFxRateService.java` (impl, reads `@ConfigurationProperties(prefix = "paypulse.fx")`) · `fx/dto/FxRateResponse.java` · `payment/api/dto/{CreatePaymentRequest,PaymentResponse}.java` (add `targetCurrency`, `convertedAmount`, `fxRate`) · `payment/api/PaymentMapper.java` · `payment/service/PaymentService.java` / validation flow for applying the hardcoded rate during create · `common/error/ErrorCode.java` — add `FX_RATE_UNAVAILABLE` (404) · `application.yml` — `paypulse.fx.rates.INR-USD: 0.012` (and reverse pair) |
| **Frontend files** | `frontend/payment-details.html`, `frontend/payments.html` — copy-to-clipboard icon next to every displayed Payment ID (`navigator.clipboard.writeText`, with "Copied!" confirmation + `execCommand` fallback) · `frontend/create-payment.html` — actual multi-currency conversion flow: source/debit currency locked to the selected account, separate `targetCurrency` choice, converted payout preview via `GET /fx/rate`, and submission of the cross-currency payment request · confirm/regression-test existing `payment-details.html?id=` and `payments.html?status=` deep links still work (already implemented, per MEM-028 audit) |
| **Tests** | `FxRateServiceTest` (unit — known pair, unknown pair) · web-layer test: `GET /fx/rate` (200/404) · payment-create tests for same-currency and cross-currency success cases, correct `convertedAmount`/`fxRate`, and `FX_RATE_UNAVAILABLE` on an unconfigured pair · manual/E2E: clipboard copy confirmation, deep-link regression check |
| **Depends on** | Nothing new |
| **Blocks** | Nothing downstream |

**Balance check:** M1 and M2 each own 3–4 sub-items (M2's two are deeper/state-machine work, M1's four are broader/shallower infra work — roughly even effort). M3/M4 each own 2 sub-items (lighter load, consistent with them absorbing more of the Core frontend-heavy work in the original split). No one is blocked waiting on another member's V2 work except the two explicitly noted dependencies (M1's load-test extension waits on M2's endpoints existing; M1's notification wiring waits on M1's own migration — internal, not cross-member).

---

## 3. Shared-File Protocol Additions — `PaymentController.java` (V2)

Extending Core's existing shared-file protocol (`13-WORK-DISTRIBUTION.md` §4) for the same class:

- **M2** adds `cancel()`, `reverse()`.
- **M3** adds `export()`.
- **M1** does **not** touch `PaymentController` for sortable columns — that's a `list()` method internal validation change already owned by whoever owns `list()` (M4 in Core); coordinate directly with M4 in stand-up before editing, since `list()` is M4's Core method.
- **M1** adds `stream()` to `AnalyticsController.java` (a **different** shared file — same protocol applies: only touch your own method + constructor injection line).

**Rule (unchanged from Core):** each PR touches only its own method(s) + the constructor injection line for its own new dependency. Pull `main` and rebase before opening a PR.

---

## 4. Day-of Plan (05 Aug → 06 Aug deadline — compressed timeline)

Given the V2 wave starts the same day as the original deadline target, this is a **single intensive push**, not a multi-day plan:

| Time block | M1 | M2 | M3 | M4 |
|---|---|---|---|---|
| **Morning** | `V6` migration + `Account` fields; `PaymentNotificationListener` skeleton | **Fix `API_BASE` bug first** (MEM-028); verify via Docker; `V7` migration + `Payment` fields | `AnalyticsService` trend deepening | `FxRateService` + `FxController` + payment contract updates for cross-currency create flow |
| **Midday** | Sortable columns (frontend + backend allow-list) | `CancelledState` + `cancel()` endpoint + frontend button | `PaymentCsvExportService` + `export()` endpoint | Copy-to-clipboard affordance |
| **Afternoon** | Dashboard SSE (backend `DashboardStreamService` + frontend `EventSource`) | `ReversalService` + `reverse()` endpoint + frontend button | Frontend CSV export button + deepened trend chart UI | Cross-currency Create Payment UI polish (preview + submit using hardcoded rate); deep-link regression check |
| **Evening** | nginx SSE config; load-test v2 extension (needs M2's endpoints) | Integration tests: cancel + reverse round trips | Tests: analytics deepening + CSV export | Tests: FX + clipboard/deep-link |
| **End-of-day — Integration Checkpoint** | **All 4:** merge to `main`, run full backend + frontend test suites, click through every V2 feature end-to-end, update `openapi.yaml`/docs if any contract drifted during implementation, walk through `04-SRS.md` §12–15 as a checklist | | | |

---

## 5. Definition of Done — V2 (per feature)

Same as Core (`13-WORK-DISTRIBUTION.md` §6) — backend + frontend + unit tests + web-layer test + green build + PR reviewed + manually clicked through — **plus, given the compressed timeline:**
1. `openapi.yaml` and `11-API-DESIGN.md` updated in the **same PR** as any endpoint change (no "update docs later").
2. Any deviation from this doc's plan (e.g. a feature turns out to need a different approach mid-implementation) is a same-day stand-up call-out, then a one-line update here — not a silent change.
3. `02-MEMORY.md` gets a new entry if a V2 decision changes from what's logged in MEM-023–034 (e.g. the instructor answers one of the new Q15–Q19 questions in `12-CLARIFICATION-QUESTIONS.md` differently than our default).

---

## 6. Risk / Dependency Notes (V2-specific)

- **M2's first task (`API_BASE` fix) is a pre-existing bug, not new work** — flagged in MEM-028 specifically so it doesn't get lost/deprioritized under the pressure of the two bigger features (#18/#19) also on M2's plate that day.
- **M1's Dashboard SSE is the largest single sub-item this wave** — if M1 runs short on time, the documented graceful-degradation fallback (MEM-027: dashboard still works via the old 30s poll if SSE isn't finished) means this can slip a few hours without breaking the demo, unlike the other items which are pass/fail.
- **Cancellation's demo-ability depends on Q15 (`12-CLARIFICATION-QUESTIONS.md`)** — until answered, M2 demos cancellation via the explicit `/validate` control endpoint rather than racing the synchronous auto-progression (MEM-029 default).
- **Reversal and Cancellation share `PaymentController.java` and `payment/domain/Payment.java`** with each other (both M2) — no cross-member conflict risk there since one person owns both, but M2 should still commit them as **separate, small PRs** (cancel first, then reversal) rather than one giant PR, for easier review under time pressure.
- **`V6`/`V7` Flyway migrations (M1/M2 respectively) must not collide** — confirm migration version numbers in stand-up before either is merged (V6 = M1/account contact fields, V7 = M2/payment cancellation+reversal fields — already assigned above to avoid a collision).
- **Notification recipient resolution (M1) has a real "no email configured" edge case** (MEM-026) — explicitly tested, never allowed to fail the payment itself; this is the most important non-functional behavior to verify under this compressed timeline (an email bug must never look like a payment bug in the demo).

---

## 7. Fill In Real Names (carried over from Core)

| Placeholder | Real Name | V2 Vertical |
|---|---|---|
| M1 | _TBD_ | Notifications wiring, Sortable Columns, Load Test v2, Dashboard SSE |
| M2 | _TBD_ | Create-Payment frontend audit, Payment Cancellation, Payment Reversal |
| M3 | _TBD_ | Analytics/Trend View (deepened), CSV Export |
| M4 | _TBD_ | Copy Payment ID/Deep Linking, Multi-Currency Conversion (hardcoded current USD rate) |

