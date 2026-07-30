# 🧪 TESTING-STRATEGY.md — Test Plan Across All Layers

Related: `04-SRS.md` §9 (Acceptance Criteria) & Appendix F of brief (Testing Considerations), `05-ARCHITECTURE.md`, `06-DESIGN-PATTERNS.md`.

> Principle (NFR-5): business logic must be testable **independent of Spring/DB**. Testing is planned per-layer from day 1, not bolted on at the end — each module owner (M1–M4) writes tests alongside their code in the same PR.

## 1. Testing Pyramid for This Project

```
        ▲  E2E / Manual demo via Swagger + React UI (few, high-value)
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
| `PaymentValidator` strategies (`AmountValidator`, `CurrencyValidator`, `AccountValidator`) | Boundary values: amount `0`, negative, `1,000,000` exactly, `1,000,000.01`, >2 decimals; unsupported currency; source==destination; malformed account format. | M1 (amount/account) / M3 (currency, wiring into chain) |
| `ValidationChain` | Runs all validators, aggregates errors correctly; short-circuits vs. collects-all behavior as designed; empty list = pass. | M3 |
| `IdempotencyService` | Returns existing payment when key matches; returns empty/proceeds when key is null or unseen. | M1 |
| `PaymentMapper` (MapStruct) | Entity ↔ DTO field mapping correctness, null-handling. | M4 |
| `PaymentService` (orchestration) | Mock `PaymentRepository` + `StatusTransitionEngine` + `IdempotencyService`; verify correct orchestration calls, correct exceptions bubble up, `@Transactional` boundaries respected (via integration test, not unit). | M2/M3 jointly (it's the seam between their modules) |

**Tools:** JUnit 5, Mockito, AssertJ. Target: fast (<5s total), run on every save.

### 2.2 Repository Layer Tests — `@DataJpaTest` (H2 in-memory, MySQL-compatibility mode)
| Target | What to test | Owner |
|---|---|---|
| `PaymentRepository` | Save/find by ID; unique constraint on `idempotency_key` throws `DataIntegrityViolationException` on duplicate; `findByStatus` filter query returns correct subset; pagination works. | M1 |
| `PaymentStatusHistoryRepository` | Insert-only behavior; ordered retrieval by `occurred_at` for a given `payment_id`. | M2 |
| Flyway migrations | Migration applies cleanly against a fresh H2/MySQL schema (smoke test on app context load). | M1 |

### 2.3 Web Layer Tests — `@WebMvcTest` + MockMvc (routes tested WITHOUT full Spring context / real DB)
| Route | What to test | Owner |
|---|---|---|
| `POST /payments` | 201 + Location header on valid input; 400 + `VALIDATION_FAILED` on missing/malformed fields (bad JSON, missing amount, negative amount, bad currency format); 200 + existing payment body on idempotency-key replay (service mocked to return "existing"). | M3 |
| `GET /payments/{id}` | 200 + correct body when service returns payment; 404 + `PAYMENT_NOT_FOUND` when service throws not-found. | M4 |
| `GET /payments/{id}/history` | 200 + ordered list; 404 if payment doesn't exist. | M2 |
| `GET /payments?status=&page=&size=` | Correct query param binding, pagination envelope shape, invalid `status` enum value → 400. | M4 |
| `POST /payments/{id}/validate|send|complete` (explicit transition endpoints, MEM-007) | Valid transition → 200 + updated payment; illegal transition (mock service throws) → 400 `INVALID_STATUS_TRANSITION`. | M2 |
| `GlobalExceptionHandler` | Each mapped exception type → correct `errorCode` + HTTP status per Appendix B table (dedicated test class hitting a throwaway controller or via one of the above routes). | M3 |

**Tools:** `@WebMvcTest`, `MockMvc`, `@MockBean` for service layer (isolates routing/serialization/validation concerns from business logic — fast, no DB).

### 2.4 Integration Tests — `@SpringBootTest` (full context, real H2/MySQL, real HTTP via `TestRestTemplate`/`MockMvc`)
| Scenario (maps to Appendix F) | What to test | Owner |
|---|---|---|
| **Happy Path** | Create payment → poll/GET → assert it reaches `COMPLETED`, history has 4 ordered entries (`CREATED→VALIDATED→SENT→COMPLETED`). | Shared (all 4 contribute one E2E scenario each in Sprint 1 final days) |
| **Validation Failures** | Negative amount, invalid currency, same source/destination account → each rejected with correct code end-to-end (DB + HTTP together). | M1/M3 |
| **Duplicate Detection** | Submit identical `idempotencyKey` twice via real HTTP → second call returns same payment ID, DB has only 1 row. | M1 |
| **Invalid State Transitions** | Attempt to call `/complete` on a `CREATED` payment → 400 `INVALID_STATUS_TRANSITION`; attempt on `COMPLETED` payment → rejected (no backwards transition). | M2 |
| **Concurrent Updates** | Two threads/requests attempting a transition on the same payment simultaneously → assert one succeeds, one gets an optimistic-lock conflict response, DB ends in a consistent single state (NFR-8). | M2 (stretch — Sprint 2 if time-constrained) |
| **DB failure simulation** | (Lower priority / stretch) Simulate repository throwing on save → assert transaction rolls back, no partial history row written. | Whoever has bandwidth in Sprint 2 |

**Tools:** `@SpringBootTest(webEnvironment = RANDOM_PORT)`, H2 (MySQL mode) by default for CI speed; optionally Testcontainers-MySQL for a final pre-demo verification run against "real" MySQL.

## 3. Test Naming Convention

`MethodOrScenario_condition_expectedResult()`, e.g. `createPayment_withDuplicateIdempotencyKey_returnsExistingPayment()`, `sentState_complete_transitionsToCompleted()`, `validate_negativeAmount_returnsInvalidAmountError()`.

## 4. Coverage Expectations (pragmatic, not dogmatic)

- **Business logic (state classes, validators, chain, idempotency service): aim for ~90%+** — this is the core of the training exercise and cheapest to test well.
- **Controllers/routes: every endpoint has at least 1 success + 1 failure case.**
- **Repositories: at least 1 test per custom query method** (standard CRUD from Spring Data doesn't need re-testing).
- Don't chase 100% line coverage on DTOs/mappers/getters — low value.

## 5. Where Tests Live (mirrors package-by-feature structure)

```
src/test/java/com/team/payments/payment/service/states/...   (M2)
src/test/java/com/team/payments/payment/service/validators/...(M1/M3)
src/test/java/com/team/payments/payment/repository/...        (M1/M2)
src/test/java/com/team/payments/payment/api/...                (M3/M4)
src/test/java/com/team/payments/integration/...                (Shared E2E scenarios)
```

## 6. CI Integration (stretch, if time allows)

- GitHub Actions workflow: on every PR → `mvn test` (unit + web-layer + `@DataJpaTest` with H2) runs automatically; full Testcontainers-MySQL suite optionally gated to `main` branch merges only (slower, avoid blocking every PR).

## 7. Definition of Done — Testing Add-on

A module (M1–M4's slice) is NOT considered done until:
1. Unit tests exist for its business logic.
2. Web-layer test exists for its route(s), covering both a success and a failure case.
3. At least one integration/E2E scenario from Appendix F is covered by *someone* by end of Sprint 1.
4. All tests green locally before opening a PR.

---
**This closes out the outstanding gap flagged before Phase 3** — testing is now planned per-layer, per-route, per-scenario, with explicit ownership, before a single line of production code is written.

