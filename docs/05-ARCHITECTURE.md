# 🏗️ ARCHITECTURE.md — System Architecture

Related: `04-SRS.md` (requirements), `06-DESIGN-PATTERNS.md` (patterns detail).

## 1. Style: Layered Architecture (with hexagonal-lite boundaries)

We adopt the **classic layered architecture** suggested in the brief's Appendix G, but with **clean boundaries** (a "hexagonal-lite" discipline) so the business logic stays framework-agnostic and testable per NFR-5.

```
┌───────────────────────────────────────────────┐
│  Web UI (React)                                │
└───────────────────┬─────────────────────────────┘
                     │ HTTP/REST (JSON)
┌───────────────────▼─────────────────────────────┐
│  API Layer (Spring Web — @RestController)        │
│  - Request/response DTOs only                    │
│  - Bean Validation annotations                    │
│  - Delegates to Service layer, no business logic  │
├───────────────────────────────────────────────┤
│  Business Logic Layer (Service + Domain)          │
│  - PaymentService (orchestration)                 │
│  - StatusTransitionEngine (State pattern)          │
│  - Validators (Strategy/Chain of Responsibility)  │
│  - IdempotencyService                              │
│  - Simulated processing (SendSimulator, etc.)      │
├───────────────────────────────────────────────┤
│  Data Access Layer (Spring Data JPA)               │
│  - PaymentRepository, PaymentHistoryRepository     │
│  - Entities (JPA) — NEVER exposed past this layer  │
├───────────────────────────────────────────────┤
│  Database — MySQL (Flyway-managed schema)          │
└───────────────────────────────────────────────┘
```

### Key rule: **dependency direction is strictly downward**. Controllers depend on Services; Services depend on Repository interfaces (not implementations); Entities never leak into the API layer (DTOs + MapStruct mappers enforce the boundary).

## 2. Package Structure (package-by-feature, layered inside)

```
com.team.payments
 ├── payment
 │    ├── api            (PaymentController, dto/, PaymentMapper)
 │    ├── domain          (Payment entity, PaymentStatus enum, PaymentStatusHistory entity)
 │    ├── service          (PaymentService, StatusTransitionEngine, states/, validators/)
 │    ├── repository       (PaymentRepository, PaymentStatusHistoryRepository)
 │    └── exception        (PaymentNotFoundException, InvalidTransitionException, ...)
 ├── common
 │    ├── error            (ErrorCode enum, ApiError DTO, GlobalExceptionHandler)
 │    ├── config           (OpenApiConfig, JacksonConfig, etc.)
 │    └── idempotency       (IdempotencyService)
 └── PaymentsApplication.java
```

**Rationale:** package-by-feature keeps each team member's (M1–M4) module cohesive and reduces merge conflicts since each person mostly touches their own subfolder (`domain`+`repository` for M1, `service/states`+audit for M2, `api`+`exception` for M3, `api` read-endpoints + OpenAPI config for M4).

## 3. Request Flow Example (Create Payment)

1. `PaymentController.create()` receives `CreatePaymentRequest` DTO, `@Valid` triggers field-level Bean Validation (format/required checks) → if invalid, Spring throws `MethodArgumentNotValidException` → caught by `GlobalExceptionHandler` → `400 VALIDATION_FAILED`.
2. Controller calls `PaymentService.createPayment(request)`.
3. `PaymentService` asks `IdempotencyService` to check for an existing payment with the given key → if found, **short-circuit, return existing** (MEM-006 → 200).
4. Otherwise, builds a `Payment` (via Builder) with status `CREATED`, saves via `PaymentRepository`, writes initial `payment_status_history` row.
5. `PaymentService` immediately invokes `StatusTransitionEngine.transition(payment, VALIDATED)` which runs the registered **validators** (Strategy/Chain of Responsibility) for business rules (currency/account/amount) → on success, moves to `VALIDATED`; on failure, moves to `FAILED` with error code — either way, audit row written.
6. (If `VALIDATED`) engine simulates send → `SENT` (or `FAILED`), then simulates completion → `COMPLETED` (or `FAILED`) — each step's own audit row.
7. Controller maps final `Payment` entity → `PaymentResponse` DTO (MapStruct) → returns to client.

This flow is exactly what will become our **sequence diagram** in Phase 3.

## 4. Cross-Cutting Concerns

| Concern | Approach |
|---|---|
| **Validation** | Two tiers: (a) Bean Validation for field-level/syntax (`@NotNull`, `@Positive`, `@Size`) at the DTO/API boundary; (b) business-rule validators (Strategy pattern) run inside the `CREATED→VALIDATED` transition. |
| **Error handling** | Single `@RestControllerAdvice` (`GlobalExceptionHandler`) maps domain exceptions → `ApiError` DTO `{errorCode, message, timestamp, path}` + correct HTTP status (Appendix B table). |
| **Idempotency** | DB-level unique constraint on `idempotency_key` + application-level pre-check (`IdempotencyService`) to short-circuit before insert attempt — avoids relying solely on catching constraint-violation exceptions. |
| **Auditability** | Every transition writes to `payment_status_history` inside the **same transaction** as the status update (`@Transactional` on the service method) — never partially applied. |
| **Concurrency** | `@Version` column on `Payment` entity → JPA optimistic locking; concurrent conflicting updates throw `OptimisticLockException` → mapped to a `409`-style conflict response. |
| **Transactions** | Service-layer methods are the transactional boundary (`@Transactional`), never the repository or controller layer. |
| **Testability** | `StatusTransitionEngine` and validators are plain Java classes/interfaces with no Spring/JPA dependency — fully unit-testable in isolation (NFR-5). |

## 5. Why NOT Hexagonal/Clean Architecture (fully)?

Considered full ports-and-adapters (hexagonal) architecture but decided **against** the full ceremony (separate module per port, explicit adapter interfaces for persistence) — it adds structural overhead not justified for a 7-day, single-bounded-context training project. We take the **useful part** of hexagonal thinking (keep domain/business logic free of framework leakage, depend on abstractions) without the full multi-module structure. Can evolve later if the project grows.

## 6. Frontend Architecture (React) — brief outline (detailed later in Phase 6)

```
src/
 ├── api/            (typed API client generated/hand-written from openapi.yaml)
 ├── features/
 │    └── payments/
 │         ├── CreatePaymentPage
 │         ├── PaymentDetailsPage (+ StatusHistoryTimeline)
 │         └── PaymentListPage (+ filters)
 ├── components/     (shared UI: StatusBadge, ErrorBanner, etc.)
 └── App.tsx / router
```

Frontend consumes the OpenAPI contract (Phase 4) directly — component structure mirrors the 3 priority screens from Appendix D.

