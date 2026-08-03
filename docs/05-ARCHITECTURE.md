# 🏗️ ARCHITECTURE.md — System Architecture

Related: `04-SRS.md` (requirements), `06-DESIGN-PATTERNS.md` (patterns detail).

> **Updated 31 Jul 2026** after the first customer meeting — additions: `Account` module, KPI/Analytics module, rate limiting, resilience (circuit breaker/retry), and security-hardening cross-cutting concerns. See `02-MEMORY.md` MEM-017–022 for rationale. The core layered structure and payment lifecycle design are **unchanged**.

## 1. Style: Layered Architecture (with hexagonal-lite boundaries)

We adopt the **classic layered architecture** suggested in the brief's Appendix G, but with **clean boundaries** (a "hexagonal-lite" discipline) so the business logic stays framework-agnostic and testable per NFR-5.

```
┌───────────────────────────────────────────────┐
│  Web UI (React) — KPI Dashboard is the landing page │
└───────────────────┬─────────────────────────────┘
                     │ HTTPS/REST (JSON)
┌───────────────────▼─────────────────────────────┐
│  Cross-cutting: RateLimitFilter → Security Headers Filter │
├───────────────────────────────────────────────┤
│  API Layer (Spring Web — @RestController)        │
│  - Request/response DTOs only                    │
│  - Bean Validation annotations                    │
│  - Delegates to Service layer, no business logic  │
├───────────────────────────────────────────────┤
│  Business Logic Layer (Service + Domain)          │
│  - PaymentService (orchestration)                 │
│  - AccountService (source-account lookup/validation) │
│  - StatusTransitionEngine (State pattern)          │
│  - Validators (Strategy/Chain of Responsibility)  │
│  - IdempotencyService                              │
│  - Simulated processing (SendSimulator, wrapped in Circuit Breaker + Retry) │
│  - AnalyticsService (KPI/dashboard aggregation queries) │
├───────────────────────────────────────────────┤
│  Data Access Layer (Spring Data JPA)               │
│  - PaymentRepository, PaymentHistoryRepository, AccountRepository │
│  - Entities (JPA) — NEVER exposed past this layer  │
├───────────────────────────────────────────────┤
│  Database — MySQL (Flyway-managed schema, InnoDB)  │
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
 ├── account               (NEW — MEM-017)
 │    ├── api              (AccountController, AccountResponse dto)
 │    ├── domain           (Account entity, AccountStatus enum)
 │    ├── service          (AccountService — lookup + ACTIVE check used by AccountValidator)
 │    └── repository       (AccountRepository)
 ├── analytics             (NEW — MEM-019)
 │    ├── api              (AnalyticsController)
 │    ├── service           (AnalyticsService — aggregation queries over payment/payment_status_history)
 │    └── dto              (KpiSummaryResponse, TrendResponse)
 ├── common
 │    ├── error            (ErrorCode enum, ApiError DTO, GlobalExceptionHandler)
 │    ├── config           (OpenApiConfig, JacksonConfig, CorsConfig, SecurityHeadersConfig)
 │    ├── ratelimit         (NEW — RateLimitFilter, Bucket4j config — MEM-020)
 │    ├── resilience        (NEW — CircuitBreaker/Retry config for simulated network calls — MEM-021)
 │    └── idempotency       (IdempotencyService)
 └── PaymentsApplication.java
```

**Rationale:** package-by-feature keeps each team member's module cohesive and reduces merge conflicts. The new `account` and `analytics` packages follow the same convention; `common/ratelimit` and `common/resilience` are cross-cutting, applied once via filters/AOP rather than duplicated per controller.

## 3. Request Flow Example (Create Payment, updated for Account selection)

1. Request passes through `RateLimitFilter` first (Bucket4j token buckets, Redis-backed (bucket4j-redis) for cross-instance correctness) — if the bucket is empty, short-circuits with `429 RATE_LIMIT_EXCEEDED` before touching the controller (cheapest possible rejection point).
2. `PaymentController.create()` receives `CreatePaymentRequest` DTO (`sourceAccountId`, `destinationAccount`, `amount`, `currency`, ...), `@Valid` triggers field-level Bean Validation (format/required checks) → if invalid, Spring throws `MethodArgumentNotValidException` → caught by `GlobalExceptionHandler` → `400 VALIDATION_FAILED`.
3. Controller calls `PaymentService.createPayment(request)`.
4. `PaymentService` asks `IdempotencyService` to check for an existing payment with the given key → if found, **short-circuit, return existing** (MEM-006 → 200).
5. `PaymentService` asks `AccountService.getActiveAccount(sourceAccountId)` — throws `AccountNotFoundException`/`InvalidAccountException` if missing or `INACTIVE` → mapped to `400/404 INVALID_ACCOUNT`.
6. Otherwise, builds a `Payment` (via Builder) with status `CREATED` and the resolved `sourceAccountId`, saves via `PaymentRepository`, writes initial `payment_status_history` row.
7. `PaymentService` immediately invokes `StatusTransitionEngine.transition(payment, VALIDATED)` which runs the registered **validators** (Strategy/Chain of Responsibility) for business rules (currency-matches-account/account-active/amount) → on success, moves to `VALIDATED`; on failure, moves to `FAILED` with error code — either way, audit row written.
8. (If `VALIDATED`) engine simulates send (wrapped in **Resilience4j CircuitBreaker + Retry**, MEM-021) → `SENT` (or `FAILED`), then simulates completion → `COMPLETED` (or `FAILED`) — each step's own audit row.
9. Controller maps final `Payment` entity → `PaymentResponse` DTO (MapStruct) → returns to client.

This flow is exactly what will become our **sequence diagram** in Phase 3 (`09-UML-SEQUENCE-DIAGRAMS.md` §8, updated).

## 4. Cross-Cutting Concerns

| Concern | Approach |
|---|---|
| **Validation** | Two tiers: (a) Bean Validation for field-level/syntax (`@NotNull`, `@Positive`, `@Size`) at the DTO/API boundary; (b) business-rule validators (Strategy pattern) run inside the `CREATED→VALIDATED` transition — now includes `AccountActiveValidator` (source account exists & `ACTIVE`) and a `CurrencyMatchesAccountValidator`. |
| **Error handling** | Single `@RestControllerAdvice` (`GlobalExceptionHandler`) maps domain exceptions → `ApiError` DTO `{errorCode, message, timestamp, path}` + correct HTTP status (Appendix B table) — **never** includes a raw stack trace or exception class name (NFR-14/security). |
| **Idempotency** | DB-level unique constraint on `idempotency_key` + application-level pre-check (`IdempotencyService`) to short-circuit before insert attempt — avoids relying solely on catching constraint-violation exceptions. |
| **Auditability / Durability** | Every transition writes to `payment_status_history` inside the **same transaction** as the status update (`@Transactional` on the service method) — never partially applied. MySQL's InnoDB engine (durable, ACID) is the only supported storage engine (NFR-13). |
| **Concurrency (at scale)** | `@Version` column on `Payment` entity → JPA optimistic locking; concurrent conflicting updates throw `OptimisticLockException` → mapped to a `409`-style conflict response. Service layer is **stateless** (no in-memory session state) so multiple instances can run behind a load balancer for horizontal scale (NFR-8/10). HikariCP connection pool sized for burst concurrency (`maximum-pool-size` tuned in `application.yml`, not left at the tiny default). |
| **Rate Limiting** *(new, MEM-020)* | `RateLimitFilter` (a `jakarta.servlet.Filter`, runs before Spring MVC dispatch) using **Bucket4j** token buckets: one global system-wide bucket sized for ~40,000 req/min, plus one per-client (IP) bucket for fair-share protection. Breach → `429` + `RATE_LIMIT_EXCEEDED` + `Retry-After`/`X-RateLimit-*` headers, without ever reaching the controller/service/DB layers (cheap to reject). |
| **Resilience** *(new, MEM-021)* | Simulated send/complete calls are wrapped with **Resilience4j** `@CircuitBreaker` + `@Retry` (bounded retries, exponential backoff) so a burst of simulated transient failures fails fast instead of exhausting request-handling threads — protects overall system reliability under load. |
| **Security** *(new, MEM-022)* | Parameterized JPA queries only (no native/concatenated SQL) — prevents injection by construction. Secure response headers via a `SecurityHeadersFilter`/Spring Security's header-only mode (`HSTS`, `X-Content-Type-Options`, `X-Frame-Options`, `Content-Security-Policy` on the React app). CORS locked to the known frontend origin(s), not `*`. Request body size limit configured (`server.tomcat.max-swallow-size` / Spring `spring.servlet.multipart.max-request-size`). Least-privilege MySQL app user (no DDL grants at runtime — Flyway migration user is separate from the app's runtime DB user). Account numbers partially masked in logs (`ACC1000****`). Dependency scanning (OWASP Dependency-Check / `npm audit`) as a CI step (stretch). |
| **Transactions** | Service-layer methods are the transactional boundary (`@Transactional`), never the repository or controller layer. |
| **Testability** | `StatusTransitionEngine` and validators are plain Java classes/interfaces with no Spring/JPA dependency — fully unit-testable in isolation (NFR-5). `RateLimitFilter` and `AnalyticsService` are also designed to be unit-testable without a full Spring context where practical. |

## 5. Why NOT Hexagonal/Clean Architecture (fully)?

Considered full ports-and-adapters (hexagonal) architecture but decided **against** the full ceremony (separate module per port, explicit adapter interfaces for persistence) — it adds structural overhead not justified for a 7-day, single-bounded-context training project. We take the **useful part** of hexagonal thinking (keep domain/business logic free of framework leakage, depend on abstractions) without the full multi-module structure. Can evolve later if the project grows.

## 6. Frontend Architecture (React) — brief outline (detailed later in Phase 6)

```
src/
 ├── api/            (typed API client generated/hand-written from openapi.yaml)
 ├── features/
 │    ├── dashboard/       (KpiDashboardPage — landing page, NEW)
 │    ├── accounts/        (useAccounts hook — powers the source-account dropdown, NEW)
 │    └── payments/
 │         ├── CreatePaymentPage (source-account dropdown, not free text)
 │         ├── PaymentDetailsPage (+ StatusHistoryTimeline)
 │         └── PaymentListPage (+ filters)
 ├── components/     (shared UI: StatusBadge, ErrorBanner, KpiCard, etc.)
 └── App.tsx / router (default route "/" → Dashboard, per customer request: KPIs shown first)
```

Frontend consumes the OpenAPI contract (Phase 4) directly — component structure mirrors the priority screens: **KPI Dashboard first**, then the 3 payment screens from Appendix D, matching the customer's explicit "show KPIs at start of the UI" direction.

## 7. Scalability, Reliability & Security Deep-Dive (new section, post-customer-meeting — MEM-020/021/022)

### 7.1 Handling ~40,000 requests/minute (NFR-10)
- **Rate limiting first** (§4) rejects excess load before it reaches business logic — the cheapest possible place to shed load.
- **Stateless services + horizontal scaling**: no session state in the app; any instance can serve any request, so a load balancer can distribute the 40k/min across N instances.
- **Connection pooling**: HikariCP pool size tuned to (DB max connections ÷ number of app instances), avoiding both pool starvation and overwhelming MySQL.
- **Indexing** (SRS §5): `status`, `created_at`, `source_account_id`, `idempotency_key` indexed — list/filter/analytics queries stay fast as row counts grow.
- **Read-heavy endpoints** (`GET /payments`, `/analytics/*`) are natural candidates for a MySQL **read replica** in a real deployment — noted as a documented scale-out path.

### 7.2 Concurrent users / concurrent requests (NFR-8)
- Optimistic locking (`@Version`) already prevents two concurrent transitions on the *same* payment from corrupting state — a retry-on-conflict strategy (bounded, e.g. 1–2 retries) is applied at the service layer before surfacing a conflict to the client.
- The idempotent-receiver pattern (MEM-006/007) is the primary defense against duplicate-submission races under concurrent load — re-validated, not redesigned, at the new scale target.
- No shared mutable state anywhere in the service layer (each request is handled by an independently-scoped Spring-managed bean graph) — this is what makes horizontal scaling safe.

### 7.3 Reliability (NFR-12) & Durability (NFR-13)
- Circuit Breaker + Retry (Resilience4j) around simulated send/complete — a burst of (simulated) failures trips the breaker, failing fast rather than piling up blocked threads, then recovers automatically (half-open probing).
- Every status transition + its audit row commit **atomically** in one transaction — no scenario leaves a payment "half-updated." MySQL's InnoDB (transactional, crash-safe) storage engine is mandatory.
- Documented future evolution (not required for Sprint 1): an outbox/event-queue pattern (already flagged as stretch in `06-DESIGN-PATTERNS.md` #11) would further decouple send/complete from the request thread for even higher durability under extreme load — a good "what we'd do with more time" presentation talking point.

### 7.4 Security (NFR-14)
See §4 "Security" row above for the concrete control list. Summary: this remains a **no-login-system** API (per brief), but is hardened against the practical, high-value subset of the OWASP API Security Top 10 that applies without introducing full auth: injection (parameterized queries), security misconfiguration (secure headers, locked-down CORS), insufficient rate limiting (§4), and information exposure through error messages (generic `ApiError` only).

