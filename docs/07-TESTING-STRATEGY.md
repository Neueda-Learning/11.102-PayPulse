# 🧪 TESTING-STRATEGY.md — Test Plan Across All Layers

Related: `04-SRS.md` §10 (Acceptance Criteria) & Appendix F of brief (Testing Considerations), `05-ARCHITECTURE.md`, `06-DESIGN-PATTERNS.md`.

> **Updated 31 Jul 2026** post-customer-meeting: added §2.5 (Account validation), §2.6 (Performance/Load testing @ 40k req/min), §2.7 (Rate-limit tests), §2.8 (Security tests) — MEM-017/019/020/021/022.

> Principle (NFR-5): business logic must be testable **independent of Spring/DB**. Testing is planned per-layer from day 1, not bolted on at the end — each module owner (M1–M4) writes tests alongside their code in the same PR.

## 1. Testing Pyramid for This Project

```
        ▲  E2E / Manual demo via Swagger + React UI (few, high-value)
        │  Load/performance tests (Gatling/JMeter, target 40k req/min) — new, run pre-demo
        │  Integration tests (@SpringBootTest, real-ish DB) — moderate count
        │  Web-layer tests (@WebMvcTest + MockMvc) — per controller/route
        │  Repository tests (@DataJpaTest, H2) — per repository
        ▼  Unit tests (plain JUnit+Mockito, no Spring context) — MOST, fastest
```

## 2. Layer-by-Layer Test Plan

### 2.1 Unit Tests — Domain/Business Logic (no Spring context, fastest, most numerous)
| Target | What to test | Owner |
|---|---|---|
| `PaymentState` implementations (`CreatedState`, `ValidatedState`, `SentState`) | Each legal transition succeeds; each illegal transition throws `InvalidStatusTransitionException`; terminal states (`CompletedState`, `FailedState`) reject ALL transitions. | M2 |
| `PaymentValidator` strategies (`AmountValidator`, `CurrencyValidator`, `AccountValidator`) | Boundary values: amount `0`, negative, `1,000,000` exactly, `1,000,000.01`, >2 decimals; unsupported currency (not `INR`/`USD`); currency ≠ source account currency; unknown/inactive `sourceAccountId`; malformed `destinationAccount` format; source==destination. | M1 (amount/account) / M3 (currency, wiring into chain) |
| `ValidationChain` | Runs all validators, aggregates errors correctly; short-circuits vs. collects-all behavior as designed; empty list = pass. | M3 |
| `IdempotencyService` | Returns existing payment when key matches; returns empty/proceeds when key is null or unseen. | M1 |
| `AccountService` *(new)* | Returns account when found & `ACTIVE`; throws/returns empty for unknown ID or `INACTIVE` account. | M1 |
| `PaymentMapper` / `AccountMapper` (MapStruct) | Entity ↔ DTO field mapping correctness, null-handling. | M4 |
| `PaymentService` (orchestration) | Mock `PaymentRepository` + `StatusTransitionEngine` + `IdempotencyService` + `AccountService`; verify correct orchestration calls, correct exceptions bubble up, `@Transactional` boundaries respected (via integration test, not unit). | M2/M3 jointly (it's the seam between their modules) |
| `AnalyticsService` *(new)* | Given a fixed set of payments/history rows, success/failure rate, avg processing time, and volume-by-currency are computed correctly (edge case: zero payments in the window → no divide-by-zero). | M4 |

**Tools:** JUnit 5, Mockito, AssertJ. Target: fast (<5s total), run on every save.

### 2.2 Repository Layer Tests — `@DataJpaTest` (H2 in-memory, MySQL-compatibility mode)
| Target | What to test | Owner |
|---|---|---|
| `PaymentRepository` | Save/find by ID; unique constraint on `idempotency_key` throws `DataIntegrityViolationException` on duplicate; `findByStatus` filter query returns correct subset; `findBySourceAccountId` works; pagination works. | M1 |
| `PaymentStatusHistoryRepository` | Insert-only behavior; ordered retrieval by `occurred_at` for a given `payment_id`. | M2 |
| `AccountRepository` *(new)* | Save/find by ID; `findByStatus(ACTIVE)`; unique constraint on `account_number`. | M1 |
| Flyway migrations | Migration applies cleanly against a fresh H2/MySQL schema, including new `account` table + `payment.source_account_id` FK (smoke test on app context load). | M1 |

### 2.3 Web Layer Tests — `@WebMvcTest` + MockMvc (routes tested WITHOUT full Spring context / real DB)
| Route | What to test | Owner |
|---|---|---|
| `POST /payments` | 201 + Location header on valid input (incl. valid `sourceAccountId`); 400 + `VALIDATION_FAILED`/`INVALID_ACCOUNT`/`INVALID_CURRENCY` on missing/malformed fields, unknown/inactive source account, or currency≠account-currency; 200 + existing payment body on idempotency-key replay (service mocked to return "existing"). | M3 |
| `GET /payments/{id}` | 200 + correct body when service returns payment; 404 + `PAYMENT_NOT_FOUND` when service throws not-found. | M4 |
| `GET /payments/{id}/history` | 200 + ordered list; 404 if payment doesn't exist. | M2 |
| `GET /payments?status=&page=&size=` | Correct query param binding, pagination envelope shape, invalid `status` enum value → 400. | M4 |
| `GET /accounts`, `GET /accounts/{id}` *(new)* | 200 + list/single account; 404 `ACCOUNT_NOT_FOUND` for unknown ID. | M1/M4 |
| `GET /analytics/summary`, `GET /analytics/trend` *(new)* | 200 + correctly-shaped KPI body (service mocked); no leakage of internal computation details on error. | M4 |
| `POST /payments/{id}/validate|send|complete` (explicit transition endpoints, MEM-007) | Valid transition → 200 + updated payment; illegal transition (mock service throws) → 400 `INVALID_STATUS_TRANSITION`. | M2 |
| `GlobalExceptionHandler` | Each mapped exception type → correct `errorCode` + HTTP status per Appendix B table, incl. new `ACCOUNT_NOT_FOUND`/`RATE_LIMIT_EXCEEDED` (dedicated test class hitting a throwaway controller or via one of the above routes). Assert response body never contains a raw stack trace/exception class name. | M3 |

**Tools:** `@WebMvcTest`, `MockMvc`, `@MockBean` for service layer (isolates routing/serialization/validation concerns from business logic — fast, no DB).

### 2.4 Integration Tests — `@SpringBootTest` (full context, real H2/MySQL, real HTTP via `TestRestTemplate`/`MockMvc`)
| Scenario (maps to Appendix F) | What to test | Owner |
|---|---|---|
| **Happy Path** | Create payment (valid `sourceAccountId`) → poll/GET → assert it reaches `COMPLETED`, history has 4 ordered entries (`CREATED→VALIDATED→SENT→COMPLETED`). | Shared (all 4 contribute one E2E scenario each in Sprint 1 final days) |
| **Validation Failures** | Negative amount, invalid currency, currency≠account-currency, unknown/inactive `sourceAccountId`, same source/destination account → each rejected with correct code end-to-end (DB + HTTP together). | M1/M3 |
| **Duplicate Detection** | Submit identical `idempotencyKey` twice via real HTTP → second call returns same payment ID, DB has only 1 row. | M1 |
| **Invalid State Transitions** | Attempt to call `/complete` on a `CREATED` payment → 400 `INVALID_STATUS_TRANSITION`; attempt on `COMPLETED` payment → rejected (no backwards transition). | M2 |
| **Concurrent Updates** | Two threads/requests attempting a transition on the same payment simultaneously → assert one succeeds, one gets an optimistic-lock conflict response, DB ends in a consistent single state (NFR-8). | M2 |
| **KPI accuracy** *(new)* | Create a mix of payments (some COMPLETED, some FAILED via deterministic trigger) → assert `/analytics/summary` reports the correct success/failure rate and volume. | M4 |
| **DB failure simulation** | (Lower priority / stretch) Simulate repository throwing on save → assert transaction rolls back, no partial history row written. | Whoever has bandwidth in Sprint 2 |

**Tools:** `@SpringBootTest(webEnvironment = RANDOM_PORT)`, H2 (MySQL mode) by default for CI speed; optionally Testcontainers-MySQL for a final pre-demo verification run against "real" MySQL.

### 2.5 Account-Specific Test Notes *(new, MEM-017)*
- Seed a fixed, known set of test accounts via a Flyway `V*__seed_test_accounts.sql` (or a dedicated test-profile migration) so integration tests have deterministic `sourceAccountId`s to reference — one `ACTIVE` INR account, one `ACTIVE` USD account, one `INACTIVE` account (for the negative test case).
- Explicitly test: creating a payment where `currency` doesn't match the source account's currency → `INVALID_CURRENCY`, not a silent conversion.

### 2.6 Performance / Load Testing *(new, MEM-020/021 — target: 40,000 req/min)*
| Target | What to test | Tooling | Owner |
|---|---|---|---|
| `POST /payments` under load | Sustained load approaching ~667 req/sec (40k/min) for a fixed duration against a local/staging instance; assert p95/p99 latency stays within an agreed budget and error rate (excluding intentional 429s) stays near zero. | Gatling or JMeter (whichever the team is faster to set up with) | M2 (owns resilience config) + whoever has bandwidth in Sprint 2 |
| Rate-limit boundary | Drive traffic just above the configured bucket capacity → assert the **excess** requests receive `429 RATE_LIMIT_EXCEEDED`, not 5xx/timeouts, and that legitimate traffic below the limit is unaffected. | Same load-test script, two traffic profiles | M2/M3 |
| Circuit breaker under simulated failure burst | Force the simulated send/complete failure rate high temporarily → assert the breaker opens (fast-fails) rather than requests hanging/timing out; assert it recovers (closes) once failures subside. | Resilience4j test utilities + a config toggle for failure rate | M2 |
| DB connection pool under load | Concurrent load test with pool sized deliberately small vs. large → confirm no `SQLTransientConnectionException` pool-starvation errors at the configured (larger) pool size. | Same load-test tooling | M1 |

> **Pragmatic note:** given the 7-day deadline, a full 40k req/min sustained run may only be feasible as a **short burst test** (e.g. 1–2 minutes) on the team's VM, not a certified capacity benchmark — the goal is to demonstrate the *design* (rate limiter + circuit breaker + pooling + indexing) behaves correctly under load, not to produce a formal SLA report.

### 2.7 Rate Limit Filter — Dedicated Unit/Web Tests *(new, MEM-020)*
- `RateLimitFilter` unit test (no Spring context): given a bucket with N tokens, the (N+1)th request within the window is rejected; tokens refill correctly after the window elapses (using a fake/controllable clock).
- Web-layer test: a mocked/very-low-capacity bucket configuration proves the `429` response shape (`errorCode=RATE_LIMIT_EXCEEDED`, `Retry-After` header present) end-to-end through the filter chain.

### 2.8 Security Tests *(new, MEM-022)*
| Target | What to test | Owner |
|---|---|---|
| SQL injection probes | Attempt injection-style strings in `reference`, `destinationAccount`, `search` query param → confirm no error/behavior change beyond normal validation (proves parameterized queries hold). | M3 |
| Error response leakage | Force a 500 (e.g. mock a repository throwing `RuntimeException`) → assert response body contains only `{errorCode, message, timestamp, path}`, never a stack trace or exception class name. | M3 |
| Security headers present | Any response includes `X-Content-Type-Options: nosniff`, `X-Frame-Options: DENY`, and CORS only allows the known frontend origin (not `*`). | M3/M4 |
| Oversized payload rejection | Submit a request body far exceeding the configured size limit → rejected cleanly (400/413), not a server crash/hang. | M3 |
| Dependency scan *(stretch, CI)* | `mvn dependency-check` / `npm audit` run in CI reports no critical/high vulnerabilities in direct dependencies. | Whoever owns CI setup |

## 3. Test Naming Convention

`MethodOrScenario_condition_expectedResult()`, e.g. `createPayment_withDuplicateIdempotencyKey_returnsExistingPayment()`, `sentState_complete_transitionsToCompleted()`, `validate_negativeAmount_returnsInvalidAmountError()`, `rateLimitFilter_exceedsBucketCapacity_returns429()`.

## 4. Coverage Expectations (pragmatic, not dogmatic)

- **Business logic (state classes, validators, chain, idempotency service, account service, analytics service): aim for ~90%+** — this is the core of the training exercise and cheapest to test well.
- **Controllers/routes: every endpoint has at least 1 success + 1 failure case.**
- **Repositories: at least 1 test per custom query method** (standard CRUD from Spring Data doesn't need re-testing).
- **Rate limiter and circuit breaker: at least 1 test proving each triggers correctly at its boundary.**
- Don't chase 100% line coverage on DTOs/mappers/getters — low value.

## 5. Where Tests Live (mirrors package-by-feature structure)

```
src/test/java/com/team/payments/payment/service/states/...   (M2)
src/test/java/com/team/payments/payment/service/validators/...(M1/M3)
src/test/java/com/team/payments/payment/repository/...        (M1/M2)
src/test/java/com/team/payments/payment/api/...                (M3/M4)
src/test/java/com/team/payments/account/...                    (M1, new)
src/test/java/com/team/payments/analytics/...                  (M4, new)
src/test/java/com/team/payments/common/ratelimit/...            (M2/M3, new)
src/test/java/com/team/payments/integration/...                (Shared E2E scenarios)
load-tests/                                                     (Gatling/JMeter scripts, new)
```

## 6. CI Integration (stretch, if time allows)

- GitHub Actions workflow: on every PR → `mvn test` (unit + web-layer + `@DataJpaTest` with H2) runs automatically; full Testcontainers-MySQL suite optionally gated to `main` branch merges only (slower, avoid blocking every PR). Dependency vulnerability scan (§2.8) as a separate, non-blocking scheduled job.

## 7. Definition of Done — Testing Add-on

A module (M1–M4's slice) is NOT considered done until:
1. Unit tests exist for its business logic.
2. Web-layer test exists for its route(s), covering both a success and a failure case.
3. At least one integration/E2E scenario from Appendix F is covered by *someone* by end of Sprint 1.
4. All tests green locally before opening a PR.

---
**This closes out the outstanding gap flagged before Phase 3** — testing is now planned per-layer, per-route, per-scenario, with explicit ownership, before a single line of production code is written, and now also covers the post-customer-meeting additions (accounts, analytics, rate limiting, resilience, security).

---

## 8. V2 Test Plan Additions (05 Aug 2026 — MEM-023–034)

### 8.1 Notification Wiring (M1, #21)
| Target | What to test |
|---|---|
| `PaymentNotificationListener` | Unit test (mock `NotificationService`): each event type (`CREATED`/`COMPLETED`/`FAILED`) triggers the correct `notifyX(...)` call; missing `owner_email` falls back to configured default; both null → notification is skipped (no exception thrown). |
| Async isolation | Integration test: a `NotificationService` mocked to throw/hang does **not** delay or fail the `POST /payments` response (assert response time unaffected, payment still persists correctly). |
| `notification_log` audit | Existing repository already covered; add a test confirming a log row is created for each of the 3 lifecycle events end-to-end. |

### 8.2 Sortable Columns (M1, #16)
| Target | What to test |
|---|---|
| `GET /payments?sort=` allow-list | Web-layer test: `sort=amount,asc`/`status,desc`/`createdAt,asc` all succeed; `sort=someInternalColumn,asc` → `400 VALIDATION_FAILED`. |
| Frontend column click | Manual/E2E: clicking each header toggles asc/desc and re-fetches with the correct `sort` param. |

### 8.3 Dashboard SSE (M1)
| Target | What to test |
|---|---|
| `DashboardStreamService` | Unit test (no Spring context where feasible): a burst of N events within the debounce window results in exactly 1 push, not N. |
| `GET /analytics/stream` | Web/integration test: client connects, a payment transition occurs, client receives an updated `KpiSummaryResponse` event within the debounce window. |
| Frontend fallback | Manual test: simulate `EventSource` failure → confirm frontend falls back to the 30s poll rather than a silently broken dashboard. |

### 8.4 Load Test v2 (M1)
| Target | What to test |
|---|---|
| Cancellation/reversal/export under load | Extend `load-tests/payments-burst.js` (or a new script) with scenarios hitting `/cancel`, `/reverse`, `/export` alongside the existing create-payment burst — confirm rate limiting and circuit breaker behavior hold consistently across the new endpoints too. |

### 8.5 Create Payment Frontend Cross-Check (M2)
| Target | What to test |
|---|---|
| `API_BASE` fix | Manual/container test: run via `docker-compose up`, confirm `frontend/js/api.js` resolves through the nginx-proxied relative path, not a hardcoded `localhost:8080` (MEM-028). |
| Contract conformance | Re-verify (as a checklist, not new automated tests): idempotency-key generation/discard lifecycle, error-shape handling, 429 handling, field names/types — all against `04-SRS.md`/`11-API-DESIGN.md`. |

### 8.6 Payment Cancellation (M2, #18)
| Target | What to test |
|---|---|
| `CancelledState` | Unit test: `CREATED→CANCELLED` succeeds; `CANCELLED` accepts no further transitions (all return/throw illegal-transition, mirroring `CompletedState`/`FailedState` tests). |
| `POST /payments/{id}/cancel` | Web-layer test: success (200 + `CANCELLED`) from `CREATED`; `409 PAYMENT_NOT_CANCELLABLE` from any other status; `404` unknown ID. |
| Integration | Create a payment, immediately cancel before auto-progression completes (may require a test-only artificial delay hook per MEM-029's flagged open question) — assert history shows `CREATED→CANCELLED`. |

### 8.7 Payment Reversal (M2, #19)
| Target | What to test |
|---|---|
| `ReversalService` | Unit test (mocked `PaymentRepository`/`PaymentService`): reversing a `COMPLETED` payment creates a new payment with swapped accounts + `reversalOfPaymentId` set, and flips `reversed=true`/`reversalPaymentId` on the original. |
| `POST /payments/{id}/reverse` | Web-layer test: success path; `409 PAYMENT_ALREADY_REVERSED` on double-reversal; `409 INVALID_STATUS_TRANSITION` on a non-`COMPLETED` payment. |
| Integration | Full round trip: create → reaches `COMPLETED` → reverse → assert both payments exist, original unchanged status, new payment independently reaches its own terminal status, history trails for both are intact and never overwritten. |

### 8.8 Analytics/Trend Deepening (M3, #13)
| Target | What to test |
|---|---|
| Deepened `AnalyticsService` trend logic | Unit tests for the new configurable-window/per-currency breakdown aggregation, including the existing zero-payments edge case. |

### 8.9 CSV Export (M3, #14)
| Target | What to test |
|---|---|
| `PaymentCsvExportService` | Unit test: correct CSV header row + escaping of commas/quotes/newlines in `reference` field. |
| `GET /payments/export` | Web-layer test: `Content-Type: text/csv`, correct row count matching filters; a filter exceeding `max-rows` → `400 EXPORT_TOO_LARGE`. |
| Memory-boundedness | Integration/manual: export a large seeded dataset and confirm (via profiler or a deliberately tiny test cap) that memory usage doesn't scale with row count beyond the streaming buffer. |

### 8.10 Copy Payment ID / Deep Linking (M4, #17)
| Target | What to test |
|---|---|
| Clipboard copy | Manual/E2E: clicking the copy icon populates the clipboard with the exact payment ID and shows the "Copied!" confirmation. |
| Deep links | Manual/E2E: `payment-details.html?id=<uuid>` loads the correct payment directly; `payments.html?status=FAILED` pre-filters correctly on load (already implemented — this is a regression-confirmation test). |

### 8.11 FX Display (M4, #20)
| Target | What to test |
|---|---|
| `FxRateService` | Unit test: known pair returns the configured rate; unknown pair → `FX_RATE_UNAVAILABLE`. |
| `GET /fx/rate` | Web-layer test: 200 + correct rate shape; 404 for an unsupported pair. |
| Frontend display hint | Manual test: create-payment/details screens show the "≈ other currency" hint without affecting the actual submitted `currency` field. |

## 9. V2 Definition of Done — Testing Add-on

Same rules as §7 apply to every V2 item above: unit tests for business logic, a web-layer test per new/changed route (success + failure), at least one integration/E2E scenario, all green before opening a PR — **plus** an explicit manual click-through against the relevant wireframe/contract before marking a V2 item done, since this wave has a shorter cycle time than Core.
