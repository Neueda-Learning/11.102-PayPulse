# 🎨 DESIGN-PATTERNS.md — Pattern Selection & Rationale

Related: `05-ARCHITECTURE.md`. This doc justifies **why** each pattern was chosen — useful both for design integrity and for the presentation ("how did you approach it").

> **Updated 31 Jul 2026** post-customer-meeting: added #12 Rate Limiter and #13 Circuit Breaker/Retry (MEM-020/021); #11 Observer is promoted from "stretch only" to a light core use (feeding the KPI dashboard) since analytics is now in scope (MEM-019).

| # | Pattern | Where Used | Why |
|---|---|---|---|
| 1 | **State Pattern** | `StatusTransitionEngine` + one class per status (`CreatedState`, `ValidatedState`, `SentState`, `CompletedState`, `FailedState`) implementing a common `PaymentState` interface with `validate()`, `send()`, `complete()`, `fail()` methods that know their own legal next-states. | The payment lifecycle IS a textbook finite state machine (brief literally suggests this in Considerations). Encapsulating transition rules inside state classes (rather than a giant if/else in the service) makes illegal transitions structurally hard to write, keeps each state's rules isolated/testable, and is easy to explain/demo live — a strong "design pattern" story for the presentation. |
| 2 | **Strategy Pattern** | `AmountValidator`, `CurrencyValidator`, `AccountValidator` (now also validates against the real `Account` entity — MEM-017) implementing a common `PaymentValidator` interface. | Each validation rule (Appendix C) varies independently and may change/extend over time (e.g. add `InsufficientFundsValidator` later). Strategy lets us add/remove/reorder rules without touching existing ones — Open/Closed Principle. |
| 3 | **Chain of Responsibility** | `ValidationChain` — wraps an ordered list of `PaymentValidator` strategies, runs them in sequence, collects/stops on first (or all) failures. | Combines cleanly with Strategy: the *chain* decides execution order & short-circuit behavior, each *link* (strategy) decides one rule. Also matches Spring's natural `List<PaymentValidator>` autowiring (Spring injects all beans of the interface type in a `List` — trivial to wire up). |
| 4 | **Repository Pattern** | `PaymentRepository`, `PaymentStatusHistoryRepository`, **`AccountRepository`** (Spring Data JPA interfaces). | Explicitly suggested by the brief (Appendix G). Decouples business logic from persistence tech — service layer talks to an interface, not to SQL/Hibernate directly. Already "free" via Spring Data, but the discipline of coding against the repository interface (not `EntityManager` directly) is what matters. |
| 5 | **DTO + Mapper Pattern (MapStruct)** | `CreatePaymentRequest`, `PaymentResponse`, `PaymentHistoryResponse`, **`AccountResponse`, `KpiSummaryResponse`** DTOs + `PaymentMapper`/`AccountMapper` interfaces. | Prevents leaking JPA entities across the API boundary (avoids lazy-loading serialization bugs, decouples API contract from DB schema so either can evolve independently). Also gives the frontend a stable, well-documented shape regardless of internal refactors. |
| 6 | **Builder Pattern** | `Payment.builder()...build()`, `Account.builder()...build()` (Lombok `@Builder`) for constructing entities with many optional fields. | Clearer than a telescoping constructor; matches JPA entity conventions well; especially useful once we have ~10 fields on `Payment`. |
| 7 | **Idempotency via "Check-then-Act" + DB Unique Constraint (belt-and-braces)** | `IdempotencyService.findExisting(key)` checked before insert; `idempotency_key` column has a DB-level `UNIQUE` constraint as a safety net against race conditions. | Not a classic GoF pattern, but a well-known **idempotent receiver** pattern from enterprise integration. Application-level check gives a friendly `200 + existing payment` response; DB constraint guarantees correctness even under a race (two near-simultaneous requests) — the DB-level violation is caught and translated to the same "return existing" response. |
| 8 | **Optimistic Locking (`@Version`)** | `Payment.version` field. | Standard JPA pattern for NFR-8 (basic concurrency safety) without the complexity/cost of pessimistic row locks — appropriate for a low-contention single-user training system, while still being a legitimate answer to "how do you handle concurrent updates?" in the presentation. Re-validated (not redesigned) against the 40k req/min scale target (MEM-021). |
| 9 | **Global Exception Handler / Controller Advice** | `GlobalExceptionHandler` (`@RestControllerAdvice`). | Centralizes API-under-failure concerns (NFR-6) — one place mapping domain exceptions → consistent `ApiError` JSON + correct HTTP status, instead of scattering try/catch across controllers. Also the single place that guarantees no stack trace/internal detail ever leaks (NFR-14/security). |
| 10 | **Dependency Injection (constructor-based)** | Everywhere (Spring beans). | Testability (can mock collaborators easily in unit tests) + explicit dependencies (no hidden field injection) — mandated in `03-SKILLS.md` coding standards. |
| 11 | **Observer / Event-driven (Spring `ApplicationEventPublisher`)** | `PaymentStatusChangedEvent` published on every transition; **`AnalyticsEventListener`** (new, MEM-019) consumes it to keep lightweight in-memory/derived KPI counters warm between on-demand aggregation queries (an optimization, not the source of truth — `GET /analytics/summary` can always recompute directly from the DB if needed). | Promoted from "stretch only" because the KPI dashboard is now core scope — Observer decouples "a transition happened" from "something needs to know about it for reporting," without the `StatusTransitionEngine` needing to know analytics exists (Open/Closed). |
| 12 | **`RateLimitFilter` (`common/ratelimit`), built on Bucket4j + bucket4j-redis (distributed, `ProxyManager<String>` backed by Redis via Lettuce) — one global bucket (~40,000 req/min) + one per-client-IP bucket, state shared across all app instances via Redis.`RateLimitFilter` (`common/ratelimit`), built on Bucket4j + bucket4j-redis (distributed, `ProxyManager<String>` backed by Redis via Lettuce) — one global bucket (~40,000 req/min) + one per-client-IP bucket, state shared across all app instances via Redis. | Directly answers the customer's "how do we implement rate limiting" question. Token bucket allows short legitimate bursts (a user submitting a payment then immediately checking status) while still capping sustained abuse — a better UX fit than a strict fixed window, and Bucket4j is a well-established, dependency-light Java library requiring no separate infrastructure for Sprint 1. |
| 13 | **Circuit Breaker + Retry (Resilience4j)** *(new, MEM-021)* | Wraps the simulated `send()`/`complete()` steps in `StatusTransitionEngine`. | Answers the customer's durability/reliability ask: if simulated downstream failures spike, the breaker "opens" and fails fast (protecting request threads/DB connections from pile-up) instead of every request hanging or retrying forever; bounded `@Retry` with backoff handles single transient blips gracefully first. Industry-standard resilience pattern, easy to explain and demo (can literally show the breaker open in logs/metrics). |

## Why this combination (not something else)?

- We deliberately picked **well-established, still industry-current** patterns (State, Strategy, Chain of Responsibility, Repository, DTO/Mapper, Builder, Optimistic Locking, Rate Limiter, Circuit Breaker) rather than chasing something exotic — the brief rewards a **solid, explainable, testable design** over cleverness, and the customer's scale/reliability/security asks are best answered with exactly these industry-standard, well-known patterns rather than bespoke solutions.
- We considered **Spring Statemachine** (a real library) as an alternative to hand-rolling the State pattern. **Decision: hand-roll it** for Sprint 1 — a dependency-free `PaymentState` interface is simpler to reason about, easier to unit-test without Spring context, and easier to explain in the presentation/diagrams than a library's internal machinery. We can swap to Spring Statemachine later as a "we evaluated X and chose to hand-roll for clarity" talking point — genuinely a legitimate architectural decision either way.
- We considered a **CQRS-style split** (separate read/write models) — rejected as over-engineering for this data size/complexity; a straightforward Repository + Service model is sufficient and more agile-appropriate. Analytics (§KPI) still computes on-demand from the same tables rather than a separate read model, for the same reason.
- We considered an **event-sourcing** approach for the audit trail (rebuild state purely from events) — elegant in theory, but higher complexity/risk for a 7-day deadline. Instead we use a simpler **"state column + append-only history table"** model which achieves the audit requirement (NFR-2) with far less machinery, while still being conceptually adjacent to event sourcing if we want to mention it as a "considered alternative" in the presentation.
- We use Bucket4j's native **bucket4j-redis** module (not a hand-rolled Lua script) for distributed rate limiting at the ~40,000 req/min target — Redis stores bucket state so multiple app instances see consistent counts; Bucket4j handles CAS atomicity internally, avoiding the race conditions a naive check-then-act implementation would have.
- We considered a **full API Gateway** (Kong, AWS API Gateway) for rate limiting + security headers instead of in-app filters — rejected for Sprint 1 as unnecessary infra overhead for a training deployment, but noted as the **production-grade evolution** of the same idea (defense-in-depth: app-level filter now, gateway-level enforcement later).

## Illustrative snippet (conceptual, not final code) — State + Strategy interplay

```java
public interface PaymentState {
    PaymentStatus name();
    Payment validate(Payment payment, ValidationChain chain); // CREATED -> VALIDATED/FAILED
    Payment send(Payment payment);                             // VALIDATED -> SENT/FAILED
    Payment complete(Payment payment);                         // SENT -> COMPLETED/FAILED
    default Payment illegalTransition() {
        throw new InvalidStatusTransitionException(...);
    }
}

public interface PaymentValidator {
    Optional<ValidationError> validate(Payment payment); // Strategy
}

public class ValidationChain {
    private final List<PaymentValidator> validators; // Spring injects all impls
    public List<ValidationError> runAll(Payment payment) { ... }
}
```

This will be formalized precisely in the **Class Diagram** (Phase 3, updated).

